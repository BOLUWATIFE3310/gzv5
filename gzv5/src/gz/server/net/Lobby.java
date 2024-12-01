package gz.server.net;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import common.db.SQLCommand;
import common.db.SQLFormater;
import common.process.ProcessQueue;
import common.process.TimeOutException;
import gz.common.logic.Game.StopReason;
import gz.server.net.Container.Group;
import gz.shared.DBV5Consts;
import gz.util.GZStruct;
import gz.util.GZUtils;

public abstract class Lobby extends Base {

	protected class InviteQuestion extends Question<Connection, String> {

		public static final int RESPONSE_INTERVAL = 30000; // 30 segundos

		private String variant;
		private boolean allowWatchers;
		private int[] options;

		public String getInvited() {
			return getQuestioned(0);
		}

		public int[] getOptions() {
			return options;
		}

		public String getVariant() {
			return variant;
		}

		public boolean isAllowingWatchers() {
			return allowWatchers;
		}

		public void open(Connection questioner, String invited, String variant, boolean allowWatchers, int[] options, QuestionTimeout<Connection, String> callback) {
			super.open(questioner, Arrays.asList(invited), RESPONSE_INTERVAL, callback);

			this.variant = variant;
			this.allowWatchers = allowWatchers;
			this.options = options;
		}
	}

	private int id;

	private String name;
	private int maxPlayers;

	private String country;
	private String groupID;
	private String variant;
	private String tournamentID;

	int ipsBlockeds;
	int accessBlockeds;
	int chatBlockeds;
	private boolean denyNewConnections;

	private ServerSocket lobbyServer;
	private ServerSocket gameServer;
	private ProcessQueue lobbyAcceptorQueue;
	private ProcessQueue gameAcceptorQueue;

	private String lobbyHost;
	private int lobbyPort;
	private String gameHost;
	private int gamePort;

	private Hashtable<Connection, LobbyGame> playing;
	private Vector<Connection> watching;
	private Vector<LobbyGame> games;
	private Hashtable<Integer, LobbyGame> gameHash;

	private Vector<InviteQuestion> inviations;

	protected Lobby() {
		super();
	}

	private void accept(ServerSocket server) {
		if (server == null)
			return;

		try {
			Socket socket = server.accept();
			socket.setSoLinger(false, 1);
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(Connection.CHECK_CONNECTION_INTERVAL);

			Connection connection = container.getConnectionClass().newInstance();
			connection.init(this, server == gameServer, socket);
		} catch (SocketException e) {
		} catch (IOException e) {
			container.handleIOException(e);
		} catch (Throwable e) {
			container.handleException(e);
		}
	}

	@Override
	protected boolean addUserInternal(Connection user) {
		synchronized (this) {
			if (isClosing() || isClosed())
				return false;
		}

		if (denyNewConnections && !user.isAdmin(id))
			return false;

		if (userCount() >= maxPlayers && !user.isAdmin(id) && !user.isPremium())
			return false;

		if (groupID != null && !user.isAdmin(id)) {
			Group group = container.getGroupContainingUser(user.getGZID());
			if (group == null || !groupID.equals(group.getID()))
				return false;

		}

		if (!super.addUserInternal(user))
			return false;

		return true;
	}

	@Override
	protected void afterClose() {
		super.afterClose();

		games.clear();
		gameHash.clear();
	}

	@Override
	protected void beforeClose() {
		super.beforeClose();

		Collection<Connection> playing;
		synchronized (connections) {
			for (Connection connection : connections)
				objectsToClose += connection.closingMaxScore();

			playing = playing();
		}

		this.playing.clear();
		this.watching.clear();

		for (Connection connection : playing) {
			int oldObjectsClosed = objectsClosed;
			logToOut("Closing " + connection + "...");
			while (true) {
				int oldScore = 0;
				try {
					connection.close(true);

					break;
				} catch (TimeOutException e) {
					int score = connection.closingScore();
					if (score <= oldScore) {
						connection.interrupt();
						container.logToErr("WARNING: The " + connection + " was forced closed.");

						break;
					}
					oldScore = score;
				} finally {
					objectsClosed = oldObjectsClosed + connection.closingScore();
				}
			}
			logToOut(connection + " closed.");
		}

		List<LobbyGame> tables;
		synchronized (games) {
			for (LobbyGame table : games)
				objectsToClose += table.closingMaxScore();

			tables = games();
		}

		for (LobbyGame table : tables) {
			int oldObjectsClosed = objectsClosed;
			logToOut(toString(), "Closing " + table + "...");
			while (true) {
				int oldScore = 0;
				try {
					table.close(true);

					break;
				} catch (TimeOutException e) {
					int score = table.closingScore();
					if (score <= oldScore) {
						table.interrupt();
						container.logToErr(toString(), "WARNING: The " + table + " was forced closed.");

						break;
					}
					oldScore = score;
				} finally {
					synchronized (this) {
						objectsClosed = oldObjectsClosed + table.closingScore();
					}
				}
			}
			logToOut(toString(), table + " closed.");
		}

		inviations.clear();

		if (lobbyServer != null)
			try {
				lobbyServer.close();
			} catch (IOException e) {
			}

		if (gameServer != null)
			try {
				gameServer.close();
			} catch (IOException e) {
			}

		// logToOut("Closing lobby acceptor queue...");
		try {
			lobbyAcceptorQueue.close(true, true);
		} catch (TimeOutException e) {
			container.logToErr("WARNING: The lobby server acceptor queue was forced closed.");
		} finally {
			logToOut("Lobby acceptor closed.");
			objectsClosed++;
		}

		logToOut("Closing game acceptor queue...");
		try {
			gameAcceptorQueue.close(true, true);
		} catch (TimeOutException e) {
			container.logToErr("WARNING: The game server acceptor queue was forced closed.");
		} finally {
			logToOut("Game acceptor closed.");
			objectsClosed++;
		}
	}

	private void cancelInviation(InviteQuestion inviation) {
		Connection sender = inviation.getQuestioner();
		if (sender.isOpen()) {
			GZStruct response = new GZStruct();
			response.setString("c", "iv_c");
			sender.postBlock(response);
		}

		Connection invited = getUserByGZID(inviation.getInvited());
		if (invited != null && invited.isOpen()) {
			GZStruct response = new GZStruct();
			response.setString("c", "ivc");
			invited.postBlock(response);
		}

		removeInviation(inviation);
	}

	public final boolean closeGame(LobbyGame table) {
		return closeGame(table, false);
	}

	public final boolean closeGame(LobbyGame table, boolean wait) {
		synchronized (this) {
			if (isClosing() || isClosed())
				return false;
		}

		if (!games.contains(table))
			return false;

		if (wait)
			post(() -> closeGameInternal(table));
		else
			send(() -> closeGameInternal(table));

		return true;
	}

	protected boolean closeGameInternal(LobbyGame game) {
		if (!games.remove(game))
			return false;

		gameHash.remove(game.getID());
		game.close();

		GZStruct response = new GZStruct();
		response.setInt("rg", game.getID());
		new BroadCast(response).send();

		return true;
	}

	protected void createGame(String variant, boolean allowWatchers, int[] options, boolean singlePlayer, Connection... players) throws IOException, InstantiationException, IllegalAccessException {
		int number = 1;
		LobbyGame game;
		synchronized (games) {
			for (LobbyGame game1 : games)
				if (game1.getNumber() == number)
					number++;

			game = container.getLobbyGameClass().newInstance();
			game.open(container, this, number, variant, allowWatchers, options, players);
			games.add(game);
			gameHash.put(game.getID(), game);
		}

		for (Connection player : players)
			if (!playing.contains(player))
				moveToPlaying(player, game, singlePlayer);

		if (allowWatchers) {
			GZStruct response = new GZStruct();
			response.setString("ag", game.getID() + "_" + game.getVariant() + "_" + getPlayersInTheGame(game));
			new BroadCast(response).send();
		}
	}

	public int gameCount() {
		return games.size();
	}

	@Override
	public int gameCount(String user) {
		synchronized (games) {
			int result = 0;
			for (LobbyGame table : games)
				if (table.containsUser(user))
					result++;

			return result;
		}
	}

	public List<LobbyGame> games() {
		synchronized (games) {
			return new ArrayList<>(games);
		}
	}

	@Override
	public List<LobbyGame> games(String user) {
		synchronized (games) {
			ArrayList<LobbyGame> result = new ArrayList<>();
			for (LobbyGame table : games)
				if (table.containsUser(user))
					result.add(table);

			return result;
		}
	}

	public LobbyGame getGameByID(int id) {
		return gameHash.get(id);
	}

	public String getGameHost() {
		return gameHost;
	}

	public int getGamePort() {
		return gamePort;
	}

	public int getID() {
		return id;
	}

	protected InviteQuestion getInviationByInvited(String invited) {
		synchronized (inviations) {
			for (InviteQuestion inviation : inviations)
				if (inviation.wasQuestioned(invited))
					return inviation;

			return null;
		}
	}

	protected InviteQuestion getInviationBySender(Connection sender) {
		synchronized (inviations) {
			for (InviteQuestion inviation : inviations)
				if (inviation.getQuestioner() == sender)
					return inviation;

			return null;
		}
	}

	@Override
	public final Lobby getLobby() {
		return this;
	}

	public String getLobbyHost() {
		return lobbyHost;
	}

	public int getLobbyPort() {
		return lobbyPort;
	}

	@Override
	public File getLogDir() {
		File homeDir = container.getHomeDir();
		String logDir = container.getLogDir();
		File file = new File(logDir);
		if (!file.isAbsolute())
			file = new File(homeDir, logDir);
		file = new File(file, "rooms");

		return file;
	}

	public int getMaxPlayers() {
		return maxPlayers;
	}

	public String getName() {
		return name;
	}

	private String getPlayersInTheGame(LobbyGame game) {
		List<String> players = game.playerNicks();
		String strPlayers;
		if (players.size() > 0) {
			strPlayers = players.get(0);
			for (int i = 1; i < players.size(); i++) {
				strPlayers += "\rvs\r";
				strPlayers += players.get(i);
			}
		} else
			strPlayers = "";

		return strPlayers;
	}

	public Connection getPlayingUserByGZID(String gzid) {
		synchronized (connections) {
			Set<Connection> connections = playing.keySet();
			for (Connection connection : connections)
				if (gzid.equals(connection.getGZID()))
					return connection;
		}

		return null;
	}
	
	public Connection getWatchingUserByGZID(String gzid) {
		synchronized (connections) {
			for (Connection connection : watching)
				if (gzid.equals(connection.getGZID()))
					return connection;
		}

		return null;		
	}

	public LobbyGame getTableByNumber(int number) {
		synchronized (games) {
			for (LobbyGame table : games)
				if (table.getNumber() == number)
					return table;

			return null;
		}
	}

	@Override
	public void interrupt() {
		try {
			lobbyServer.close();
		} catch (IOException e) {
		}

		try {
			gameServer.close();
		} catch (IOException e) {
		}

		super.interrupt();
	}

	public boolean isPlaying(Connection user) {
		return playing.containsKey(user);
	}
	
	public boolean isWatching(Connection user) {
		return watching.contains(user);
	}

	public boolean isPlaying(String gzid) {
		return getPlayingUserByGZID(gzid) != null;
	}

	private void moveToPlaying(Connection user, LobbyGame game, boolean singlePlayer) {
		super.removeConnectionInternal(user, true);

		GZStruct response = new GZStruct();
		if (game != null) {
			playing.put(user, game);
			response.setString("c", singlePlayer ? "nwg" : "nwgm");
			response.setInt("ig", game.getID());
			response.setString("res", "ok");
		} else {
			watching.add(user);
			response.setString("c", "wt");
		}

		user.postBlock(response);
	}

	@Override
	protected void onChat(String sender, String senderNick, String receiver, String message) {
		try {
			senderNick = URLEncoder.encode(senderNick, "utf-8");
		} catch (UnsupportedEncodingException e) {
			container.handleException(e);
		}

		try {
			message = URLEncoder.encode(message, "utf-8");
		} catch (UnsupportedEncodingException e) {
			container.handleException(e);
		}

		try {
			String sn = senderNick;
			String msg = message;
			container.executeTransaction((connection) -> connection.execute(SQLCommand.insert(DBV5Consts.TABLE_LOBBY_CHAT_LOG,
					new Object[] { null, container.getGameName(), getID(), null, sender, sn, receiver, msg, new Timestamp(System.currentTimeMillis()) })));
		} catch (SQLException e) {
			container.handleException(e);
		} catch (InterruptedException e) {
		}
	}

	@Override
	protected void onConnectionAdded(Connection connection, GZStruct response) {
		super.onConnectionAdded(connection, response);

		synchronized (games) {
			GZStruct gl = new GZStruct();
			for (LobbyGame game : games) {
				if (!game.allowWatchers())
					continue;

				String s = game.getVariant() + "_" + getPlayersInTheGame(game);
				gl.setString(Integer.toString(game.getID()), s);
			}

			//response.setStruct("gl", gl);
		}

		getContainer().kickFromAllLobbies(connection, this);
	}
	
	protected void onConnectionRemoved(Connection connection) {
		super.onConnectionRemoved(connection);
		
		InviteQuestion inviation = getInviationByInvited(connection.getGZID());
		if (inviation != null)
			rejectInviation(inviation);
		
		inviation = getInviationBySender(connection);
		if (inviation != null)
			cancelInviation(inviation);
	}

	protected void open(Container container, int id, String name, String country, String groupID, String variant, String tournamentID, int maxPlayers, String lobbyHost, int lobbyPort, String gameHost,
			int gamePort) throws IOException {
		this.id = id;
		this.name = name;
		this.country = country;
		this.groupID = groupID;
		this.variant = variant;
		this.tournamentID = tournamentID;
		this.maxPlayers = maxPlayers;
		this.lobbyHost = lobbyHost;
		this.lobbyPort = lobbyPort;
		this.gameHost = gameHost;
		this.gamePort = gamePort;

		super.open(new ThreadGroup(container.getGroup(), toString()), container);

		games = new Vector<>();
		gameHash = new Hashtable<>();
		playing = new Hashtable<>();
		watching = new Vector<>();
		inviations = new Vector<>();

		getQueue().setName("Lobby queue [" + name + "]");

		denyNewConnections = false;
		ipsBlockeds = 0;
		accessBlockeds = 0;
		chatBlockeds = 0;

		try {
			container.executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery("(SELECT * FROM " + DBV5Consts.TABLE_LOBBY_CHAT_LOG + " WHERE game=" + SQLFormater.formatValue(container.getGameName()) + " AND lobby="
						+ getID() + " AND game_id IS NULL ORDER BY sent_when DESC LIMIT " + MAX_CHAT_HISTORY + ") ORDER BY sent_when ASC")) {
					while (rs.next()) {
						String sender = rs.getString("sender");

						String senderNick = rs.getString("sender_nick");
						try {
							senderNick = URLDecoder.decode(senderNick, "utf-8");
						} catch (UnsupportedEncodingException e) {
							container.handleException(e);
						}

						String receiver = rs.getString("receiver");

						String message = rs.getString("message");
						try {
							message = URLDecoder.decode(message, "utf-8");
						} catch (UnsupportedEncodingException e) {
							container.handleException(e);
						}

						Timestamp sentWhen = rs.getTimestamp("sent_when");

						addChat(sender, senderNick, receiver, message, sentWhen.getTime());
					}
				}

				if (groupID != null) {
					try (ResultSet rs = connection.executeQuery("SELECT COUNT(*) FROM " + DBV5Consts.TABLE_BANS + " WHERE game=" + SQLFormater.formatValue(container.getGameName()) + " AND room=" + id
							+ " AND restriction=\"ACCESS\"" + " AND type=\"GZID,COMPID,IP\"" + " AND (duration=-1 OR DATE_ADD(" + DBV5Consts.TABLE_BANS
							+ ".when, INTERVAL duration / 1000 SECOND) > NOW())" + " AND unbanned_when IS NULL")) {
						if (rs.next())
							ipsBlockeds = rs.getInt(1);
					}

					try (ResultSet rs = connection.executeQuery("SELECT COUNT(*) FROM " + DBV5Consts.TABLE_BANS + " WHERE game=" + SQLFormater.formatValue(container.getGameName()) + " AND room=" + id
							+ " AND restriction=\"ACCESS\"" + " AND type=\"GZID\"" + " AND (duration=-1 OR DATE_ADD(" + DBV5Consts.TABLE_BANS + ".when, INTERVAL duration / 1000 SECOND) > NOW())"
							+ " AND unbanned_when IS NULL")) {
						if (rs.next())
							accessBlockeds = rs.getInt(1);
					}

					try (ResultSet rs = connection.executeQuery("SELECT COUNT(*) FROM " + DBV5Consts.TABLE_BANS + " WHERE game=" + SQLFormater.formatValue(container.getGameName()) + " AND room=" + id
							+ " AND restriction=\"CHAT\"" + " AND type=\"GZID\"" + " AND (duration=-1 OR DATE_ADD(" + DBV5Consts.TABLE_BANS + ".when, INTERVAL duration / 1000 SECOND) > NOW())"
							+ " AND unbanned_when IS NULL")) {
						if (rs.next())
							chatBlockeds = rs.getInt(1);
					}
				}

				return null;
			});
		} catch (SQLException e) {
			container.handleException(e);
		} catch (InterruptedException e) {
		}

		lobbyAcceptorQueue = new ProcessQueue(getGroup(), "Lobby server acceptor queue");
		lobbyAcceptorQueue.post(() -> {
			try {
				InetAddress addr = InetAddress.getByName(this.lobbyHost);
				logToOut("Opening lobby server with bind address " + addr.getHostName() + ":" + this.lobbyPort);
				lobbyServer = new ServerSocket(this.lobbyPort, 50, addr);
			} catch (IOException e) {
				container.handleIOException(e);
				return;
			}
		}, (e) -> container.logToErr(e));
		lobbyAcceptorQueue.post(() -> accept(lobbyServer), 0, (e) -> container.logToErr(e));

		gameAcceptorQueue = new ProcessQueue(getGroup(), "Game server acceptor queue");
		gameAcceptorQueue.post(() -> {
			try {
				InetAddress addr = InetAddress.getByName(this.gameHost);
				logToOut("Opening game server with bind address " + addr.getHostName() + ":" + this.gamePort);
				gameServer = new ServerSocket(this.gamePort, 50, addr);
			} catch (IOException e) {
				container.handleIOException(e);
				return;
			}
		}, (e) -> container.logToErr(e));
		gameAcceptorQueue.post(() -> accept(gameServer), 0, (e) -> container.logToErr(e));
	}

	@Override
	protected void parseDataInternal(Connection user, String opcode, GZStruct request) throws Throwable {
		switch (opcode) {
			case "iv": { // inviation request
				if (!connections.contains(user))
					break;

				InviteQuestion inviation = getInviationBySender(user);
				if (inviation != null)
					break;

				String invited = request.getString("i");
				String variant = request.getString("t");
				String p = request.getString("p");
				String[] parameters = p.split("_");

				if (variant == null || !validateVariant(variant)) {
					GZStruct response = new GZStruct();
					response.setString("c", "ivr");
					user.postBlock(response);
					break;
				}

				boolean allowWatchers = parameters[0].equals("1");

				int[] options = new int[parameters.length - 1];
				for (int i = 0; i < parameters.length - 1; i++)
					options[i] = Integer.parseInt(parameters[i + 1]);

				if (!validateOptions(options)) {
					user.postClose();
					return;
				}

				Connection invitedConnection = getUserByGZID(invited);
				if (invitedConnection == null) {
					GZStruct response = new GZStruct();
					response.setString("c", "ivq");
					user.postBlock(response);
					break;
				}

				int invitedStatus = invitedConnection.getOnlineStatus();
				if (invitedStatus == 4 || invitedStatus == 5) {
					GZStruct response = new GZStruct();
					response.setString("c", "ivr");
					user.postBlock(response);
					break;
				}

				if (wasInvited(invited) || isInviting(invitedConnection)) {
					GZStruct response = new GZStruct();
					response.setString("c", "ivt");
					user.postBlock(response);
					break;
				}

				inviation = new InviteQuestion();
				inviations.add(inviation);
				inviation.open(user, invited, variant, allowWatchers, options, (question, accepteds) -> cancelInviation((InviteQuestion) question));

				GZStruct response = new GZStruct();
				response.setString("c", "iv_w");
				user.postBlock(response);

				response = new GZStruct();
				response.setString("c", "iv");
				response.setString("i", user.getGZID());
				response.setString("u", user.getGZID());
				response.setString("n", user.getNick());
				response.setString("t", variant);
				response.setBoolean("w", allowWatchers);
				response.setString("p", GZUtils.join(options, "_"));
				invitedConnection.postBlock(response);

				break;
			}

			case "civ": { // cancel inviation request
				if (!connections.contains(user))
					break;

				InviteQuestion inviation = getInviationBySender(user);
				if (inviation == null)
					break;

				cancelInviation(inviation);

				break;
			}

			case "aiv": { // accept inviation
				if (!connections.contains(user))
					break;

				String sender = request.getString("i");
				Connection senderConnection = getUserByGZID(sender);
				if (senderConnection == null || !senderConnection.isOpen())
					break;

				InviteQuestion inviation = getInviationBySender(senderConnection);
				if (inviation == null)
					break;

				boolean allowWatchers = inviation.isAllowingWatchers();
				int[] options = inviation.getOptions();

				inviation.close();
				inviations.remove(inviation);

				GZStruct response = new GZStruct();
				response.setString("c", "iv_a");
				user.postBlock(response);

				response = new GZStruct();
				response.setString("c", "iv_a");
				senderConnection.postBlock(response);

				createGame(inviation.getVariant(), allowWatchers, options, false, senderConnection, user);

				break;
			}

			case "riv": { // reject inviation
				if (!connections.contains(user))
					break;

				InviteQuestion inviation = getInviationByInvited(user.getGZID());
				if (inviation == null)
					break;

				rejectInviation(inviation);

				break;
			}

			case "wt": { // watch game request
				if (!connections.contains(user))
					break;

				moveToPlaying(user, null, false);
				break;
			}

			case "ret": { // returning to lobby
				LobbyGame game = playing.remove(user);
				if (game == null) {
					if (!watching.remove(user)) {
						user.postClose();
						break;
					}
				}
				else
					game.removeConnection(user, true);

				int s = request.getInt("s");
				if (s < 1 || s > 5)
					break;

				if (!user.isPremium() && (s == 2 || s == 4 || s == 5))
					break;
				
				user.setOnlineStatus(s);

				user.refresh();
				if (!addUserInternal(user)) {
					user.postClose();
					break;
				}

				GZStruct response = new GZStruct();
				onConnectionAdded(user, response);
				user.postBlock(response);

				break;
			}

			default:
				super.parseDataInternal(user, opcode, request);
		}
	}

	public Collection<Connection> playing() {
		return playing(true);
	}

	protected Collection<Connection> playing(boolean copy) {
		if (copy)
			synchronized (playing) {
				return new ArrayList<>(playing.keySet());
			}

		return Collections.unmodifiableCollection(playing.keySet());
	}
	
	protected Collection<Connection> watching(boolean copy) {
		if (copy)
			synchronized (watching) {
				return new ArrayList<>(watching);
			}

		return Collections.unmodifiableCollection(watching);
	}

	public int playingCount() {
		return playing.size();
	}
	
	public int watchingCount() {
		return watching.size();
	}

	private void rejectInviation(InviteQuestion inviation) {
		Connection sender = inviation.getQuestioner();
		if (sender.isOpen()) {
			GZStruct response = new GZStruct();
			response.setString("c", "ivr");
			sender.postBlock(response);
		}

		Connection invited = getUserByGZID(inviation.getInvited());
		if (invited != null && invited.isOpen()) {
			GZStruct response = new GZStruct();
			response.setString("c", "iv_r");
			invited.postBlock(response);
		}

		removeInviation(inviation);
	}

	private void removeInviation(InviteQuestion inviation) {
		inviations.remove(inviation);
		inviation.close();
	}

	@Override
	protected boolean removeConnectionInternal(Connection user, boolean normalExit) {
		LobbyGame game = playing.remove(user);
		boolean result = false;
		if (game != null)
			result = game.removeConnectionInternal(user, normalExit);
		else
			result = watching.remove(user);
			
		result |= super.removeConnectionInternal(user, normalExit);
		return result;
	}

	public final void stopAllGames() {
		stopAllGames(StopReason.NORMAL);
	}

	public final void stopAllGames(StopReason reason) {
		synchronized (games) {
			for (LobbyGame table : games)
				table.getGame().stop(reason);
		}
	}

	@Override
	public String toString() {
		return "room " + name;
	}

	@Override
	public int userCount() {
		return super.userCount() + playingCount() + watchingCount();
	}

	protected abstract boolean validateOptions(int[] options);

	protected abstract boolean validateVariant(String variant);

	public boolean wasInvited(String gzid) {
		return getInviationByInvited(gzid) != null;
	}
	
	public boolean isInviting(Connection connection) {
		return getInviationBySender(connection) != null;
	}

	public String getCountry() {
		return country;
	}

	public String getGroupID() {
		return groupID;
	}

	public boolean isPublic() {
		return country == null && groupID == null && variant == null && tournamentID == null;
	}

	public void alertAllPlayers(String message) {
		super.alertAllPlayers(message);

		synchronized (games) {
			for (LobbyGame game : games)
				game.alertAllPlayers(message);
		}
	}

	protected void kickInternal(String gzid) {
		super.kickInternal(gzid);

		synchronized (games) {
			for (LobbyGame game : games)
				game.kick(gzid);
		}
	}

	public String getVariant() {
		return variant;
	}

	public String getTournamentID() {
		return tournamentID;
	}

	public int getIPsBlockeds() {
		return ipsBlockeds;
	}

	public int getAccessBlockeds() {
		return accessBlockeds;
	}

	public int getChatBlockeds() {
		return chatBlockeds;
	}

	public boolean isDenyingNewConnections() {
		return denyNewConnections;
	}

	public void disconnectAll(Connection except, boolean exceptAdmins) {
		super.disconnectAll(except, exceptAdmins);

		synchronized (games) {
			for (LobbyGame game : games)
				game.disconnectAll(except, exceptAdmins);
		}
	}

	public void allowNewConnections() {
		denyNewConnections = false;
		logToOut(toString(), "[INFO] Allowing new connections");
	}

	public void denyNewConnections() {
		denyNewConnections = true;
		logToOut(toString(), "[INFO] Denying new connections");
	}

	public void resetBlockedsByIP(String admin) {
		ipsBlockeds = 0;

		logToOut(toString(), "[INFO] Reseting blockeds by ip");

		try {
			container.executeTransaction((connection) -> {
				connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_BANS + " SET unbanned_when=NOW()," + " unbanned_by=" + SQLFormater.formatValue(admin) + ","
						+ " unbanned_reason=\"reseting blockeds by ip\"" + " WHERE game=" + SQLFormater.formatValue(container.getGameName()) + " AND room=" + id + " AND type=\"GZID,COMPID,IP\""
						+ " AND restriction=\"ACCESS\"" + " AND (duration=-1 OR DATE_ADD(" + DBV5Consts.TABLE_BANS + ".when, INTERVAL duration / 1000 SECOND) > NOW())" + " AND unbanned_when IS NULL");
				return null;
			});
		} catch (SQLException e) {
			container.handleException(e);
		} catch (InterruptedException e) {
		}
	}

	public void resetBlockedsByAccess(String admin) {
		accessBlockeds = 0;

		logToOut(toString(), "[INFO] Reseting blockeds by access");

		try {
			container.executeTransaction((connection) -> {
				connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_BANS + " SET unbanned_when=NOW()," + " unbanned_by=" + SQLFormater.formatValue(admin) + ","
						+ " unbanned_reason=\"reseting blockeds by ip\"" + " WHERE game=" + SQLFormater.formatValue(container.getGameName()) + " AND room=" + id + " AND type=\"GZID\""
						+ " AND restriction=\"ACCESS\"" + " AND (duration=-1 OR DATE_ADD(" + DBV5Consts.TABLE_BANS + ".when, INTERVAL duration / 1000 SECOND) > NOW())" + " AND unbanned_when IS NULL");
				return null;
			});
		} catch (SQLException e) {
			container.handleException(e);
		} catch (InterruptedException e) {
		}
	}

	public void resetBlockedsByChat(String admin) {
		chatBlockeds = 0;

		logToOut(toString(), "[INFO] Reseting blockeds for the chat");

		try {
			container.executeTransaction((connection) -> {
				connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_BANS + " SET unbanned_when=NOW()," + " unbanned_by=" + SQLFormater.formatValue(admin) + ","
						+ " unbanned_reason=\"reseting blockeds by ip\"" + " WHERE game=" + SQLFormater.formatValue(container.getGameName()) + " AND room=" + id + " AND type=\"GZID\""
						+ " AND restriction=\"CHAT\"" + " AND (duration=-1 OR DATE_ADD(" + DBV5Consts.TABLE_BANS + ".when, INTERVAL duration / 1000 SECOND) > NOW())" + " AND unbanned_when IS NULL");
				return null;
			});
		} catch (SQLException e) {
			container.handleException(e);
		} catch (InterruptedException e) {
		}
	}

	public void open() throws IOException {
		open(container, id, name, country, groupID, variant, tournamentID, maxPlayers, lobbyHost, lobbyPort, gameHost, gamePort);
	}

}
