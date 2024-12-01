package gz.server.net;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import common.db.SQLCommand;
import common.process.ProcessQueue;
import common.process.ReturnableProcess;
import common.process.timer.Timer;
import common.util.BitUtil;
import common.util.DebugUtil;
import gz.common.logic.Game.StopReason;
import gz.common.logic.GameController;
import gz.common.logic.MultiplayerGame;
import gz.common.logic.Player;
import gz.common.logic.RatingSystem;
import gz.shared.DBV5Consts;
import gz.util.GZStruct;

@SuppressWarnings("unused")
public abstract class LobbyGame extends Base {

	public static final int RATING_GAIN = 200;

	protected class Controller implements GameController {

		@Override
		public ProcessQueue getQueue() {
			return LobbyGame.this.getQueue();
		}

		@Override
		public void onChangeTurn(int turn) {

		}

		@Override
		public void onNextRound(int round) {

		}

		@Override
		public void onRotateLeft() {
			Seat first = seats[0];
			for (int index = 0; index < seats.length - 1; index++) {
				seats[index] = seats[index + 1];
				if (seats[index] != null)
					seats[index].index = index;
			}

			seats[seats.length - 1] = first;
			if (first != null)
				first.index = seats.length - 1;
		}

		@Override
		public void onRotateRight() {
			Seat last = seats[seats.length - 1];
			for (int index = seats.length - 1; index > 0; index--) {
				seats[index] = seats[index - 1];
				if (seats[index] != null)
					seats[index].index = index;
			}

			seats[0] = last;
			if (last != null)
				last.index = 0;
		}

		@Override
		public void onStart() {
			logToOut("The game #" + game.getGameNumber() + " has started");
			post(() -> onStartGame());
		}

		@Override
		public void onStarted() {

		}

		@Override
		public void onStop(StopReason reason) {
			onStopGame(reason);
		}

		@Override
		public void onSwap(int index1, int index2) {
			Seat temp = seats[index1];
			seats[index1] = seats[index2];
			seats[index2] = temp;

			if (seats[index1] != null)
				seats[index1].index = index1;
			if (seats[index2] != null)
				seats[index2].index = index2;
		}

	}

	public abstract class Seat {

		private int index;
		private Connection user;
		private boolean focused;
		private Player player;
		private boolean leaving;
		private boolean wantToStart;
		private int oldRating;
		private int oldRating2;
		private boolean closed;
		private int wins;
		private int losses;

		protected Seat(int index, Connection user) {
			this.index = index;
			this.user = user;

			focused = true;
			wantToStart = false;
			closed = false;
		}

		public final synchronized boolean canStart() {
			return !leaving && game.canJoin(index, user != null ? user.getGZID() : "COMPUTER");
		}

		private synchronized void checkClosed() {
			if (closed)
				throw new RuntimeException("The seat " + index + " is currently closed.");
		}

		public synchronized Connection getConnection() {
			checkClosed();

			return user;
		}

		public int getIndex() {
			checkClosed();

			return index;
		}

		public synchronized Player getPlayer() {
			checkClosed();

			return player;
		}

		public synchronized boolean isFocused() {
			checkClosed();

			return focused;
		}

		protected synchronized boolean isLeaving() {
			return leaving;
		}

		public synchronized boolean isPlaying() {
			checkClosed();

			return player != null && player.isPlaying();
		}

		protected synchronized void leave() {
			if (leaving || closed)
				return;

			leaving = true;
			try {
				if (DEBUG && DebugUtil.DEBUG_MODE)
					System.out.println("Seat [" + index + "] " + (user != null ? user : "COMPUTER") + " leaving");

				if (player != null)
					player.leave();
			} finally {
				player = null;
				closed = true;
				leaving = false;
			}
		}

		public synchronized void setFocused(boolean value) {
			checkClosed();

			if (focused == value)
				return;

			focused = value;
		}

		public final void standUp() {
			checkClosed();

			post(() -> standUpInternal(index, false));
		}

		public final synchronized boolean start() {
			checkClosed();

			player = game.join(index, user != null ? user.getGZID() : "COMPUTER");
			if (DEBUG && DebugUtil.DEBUG_MODE)
				System.out.println("Seat [" + index + "] " + user + " started the game and received the player descriptor: " + player);

			if (player != null)
				return player.start();

			return false;
		}

		@Override
		public String toString() {
			return (user != null ? user : "COMPUTER") + " at #" + index;
		}

		public boolean wantToStart() {
			return wantToStart;
		}

		public int getRating() {
			Connection user = getConnection();
			return user != null ? user.getRating() : 0;
		}

		public int getRating2() {
			Connection user = getConnection();
			return user != null ? user.getRating2() : 0;
		}

	}

	protected class SeatBroadCast {
		GZStruct out;
		List<Seat> seats;
		Seat ignore;

		private SeatBroadCast(GZStruct out) {
			this(out, null, null);
		}

		public SeatBroadCast(GZStruct out, List<Seat> seats) {
			this(out, seats, null);
		}

		public SeatBroadCast(GZStruct out, List<Seat> seats, Seat ignore) {
			this.out = out;
			this.seats = seats;
			this.ignore = ignore;
		}

		private SeatBroadCast(GZStruct out, Seat ignore) {
			this(out, null, ignore);
		}

		public void close() {
			out = null;
			ignore = null;
		}

		protected void flush(boolean close) {
			synchronized (LobbyGame.this.seats) {
				ArrayList<Connection> broadcastedConnections = new ArrayList<>();
				for (Seat seat : LobbyGame.this.seats) {
					if (seat == null)
						continue;

					Connection user = seat.getConnection();
					if (user == null)
						continue;

					if (broadcastedConnections.contains(user))
						continue;

					if (ignore != null && ignore.equals(seat))
						continue;

					if (seats != null && !seats.contains(seat))
						continue;

					user.postBlock(out, close);
					broadcastedConnections.add(user);
				}
			}
		}
	}

	protected class SeatQuestion extends Question<Seat, Seat> {

		public static final int RESPONSE_INTERVAL = 15000; // 15 segundos

		public SeatQuestion() {
			super();
		}

		public synchronized int getAcceptedBits() {
			Seat questioner = getQuestioner();
			int result = questioner != null ? 1 << questioner.getIndex() : 0;

			for (int i = 0; i < getQuestionedCount(); i++) {
				Seat questioned = getQuestioned(i);
				if (getResponse(i) == ACCEPTED)
					result |= 1 << questioned.getIndex();
			}

			return result;
		}

		public synchronized int getRejectedBits() {
			int result = 0;

			for (int i = 0; i < getQuestionedCount(); i++) {
				Seat questioned = getQuestioned(i);
				if (getResponse(i) == REJECTED)
					result |= 1 << questioned.getIndex();
			}

			return result;
		}

		public void open(Seat questioner, List<Seat> questioneds, QuestionTimeout<Seat, Seat> callback) {
			super.open(questioner, questioneds, RESPONSE_INTERVAL, callback);
		}

	}

	public static final int DEFAULT_TIME_PER_TURN_SEC = 60;
	public static final int[] MATCH_TIMES = { 1, 2, 5, 10, 15 };
	public static final int[] TIMES_PER_MOVE = { 5, 10, 15, 30, 60, 120 };
	protected static final int TIME_PER_MOVE_TOLERANCE = 5;
	protected static final int MATCH_TIME_TOLERANCE = 1;
	public static final long MINIMUM_TIME_TO_RATED = 30 * 1000; // 30 seconds
	private static final int DEFAULT_WINS_QUERY_PERIOD_SEC = 24 * 60 * 60; // 1
																			// day
	private static final int CHECK_FIRST_GAME_STARTED_INTERVAL = 10 * 1000; // 10
																			// seconds

	protected static final boolean DEBUG = true;

	private static AtomicInteger ID = new AtomicInteger(1000);

	public static final int PUBLIC = 0;
	public static final int PRIVATE = 1;

	protected static final int NO_REASON = 0;
	protected static final int NO_ENOUGH_PLAYERS = 1;

	private Lobby lobby;
	private int number;
	private ArrayList<String> playerGZIDs;
	private ArrayList<String> playerNicks;

	protected int privacy;
	protected boolean noWatches;

	private int oldPrivacy;
	private boolean oldNoWatches;

	private int id;
	protected boolean gameSetup;
	protected int matchTime = 0;
	protected int timePerMove = 0;
	protected boolean allowWatchers;
	private long startTime;

	protected Seat[] seats;

	protected Vector<Connection> watchers;

	protected SeatQuestion startGameQuestion;
	private SeatQuestion continueGameQuestion;

	private boolean rated;
	private MultiplayerGame game;
	protected String variant;
	private RatingSystem<Seat> system;

	private Timer tmrCheckStarted;

	protected LobbyGame() {

	}

	protected void acceptStartGame(Seat seat) {
		seat.wantToStart = true;

		if (continueGameQuestion == null)
			return;

		synchronized (continueGameQuestion) {
			if (continueGameQuestion.isOpen()) {
				if (!continueGameQuestion.accept(seat))
					return;

				if (continueGameQuestion.allOpined() && continueGameQuestion.getAcceptedCount() > 1) {
					List<Seat> accepteds = new ArrayList<>();
					for (int i = 0; i < continueGameQuestion.getQuestionedCount(); i++)
						accepteds.add(continueGameQuestion.getQuestioned(i));

					continueGameQuestion.close();

					startGame(null, accepteds);
				}

				return;
			}
		}

		if (startGameQuestion == null)
			return;

		synchronized (startGameQuestion) {
			if (!startGameQuestion.isOpen() || !startGameQuestion.accept(seat))
				return;

			if (startGameQuestion.allOpined() && startGameQuestion.getAcceptedCount() > 0) {
				Seat questioner = startGameQuestion.getQuestioner();
				List<Seat> accepteds = new ArrayList<>();
				accepteds.add(startGameQuestion.getQuestioner());
				for (int i = 0; i < startGameQuestion.getQuestionedCount(); i++)
					accepteds.add(startGameQuestion.getQuestioned(i));

				startGameQuestion.close();

				startGame(questioner, accepteds);
			}
		}
	}

	@Override
	protected void afterClose() {
		super.afterClose();

		lobby = null;
		game = null;
		startGameQuestion = null;
		continueGameQuestion = null;
		tmrCheckStarted = null;
	}

	public boolean allowWatchers() {
		return allowWatchers;
	}

	@Override
	protected void beforeClose() {
		super.beforeClose();

		tmrCheckStarted.close();

		game.stop(StopReason.CANCELED);

		synchronized (seats) {
			for (int i = 0; i < seats.length; i++)
				standUpInternal(i, true);
		}

		game.close();

		watchers.clear();

		startGameQuestion.terminate();
		continueGameQuestion.terminate();

		lobby.closeGame(this);
	}

	protected abstract MultiplayerGame createGame();

	protected abstract Player[] createPlayers();

	protected abstract Seat createSeat(int seatIndex, Connection user);

	protected abstract Seat[] createSeats();

	protected void defaultConfig() {
		privacy = PUBLIC;
		noWatches = false;
	}

	protected void dumpGameState() {

	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof LobbyGame))
			return false;
		LobbyGame other = (LobbyGame) obj;
		if (game == null) {
			if (other.game != null)
				return false;
		} else if (!game.equals(other.game))
			return false;
		if (id != other.id)
			return false;
		if (lobby == null) {
			if (other.lobby != null)
				return false;
		} else if (!lobby.equals(other.lobby))
			return false;
		return true;
	}

	@Override
	public int gameCount(String user) {
		return lobby.gameCount(user);
	}

	@Override
	public List<LobbyGame> games(String user) {
		return lobby.games(user);
	}

	public synchronized int getCurrentTime() {
		return game.getCurrentTimePerTurn();
	}

	public int getFocusedFlags() {
		synchronized (seats) {
			int result = 0;
			for (int i = 0; i < seats.length; i++)
				if (seats[i] != null && seats[i].isFocused())
					result |= 1 << i;

			return result;
		}
	}

	protected MultiplayerGame getGame() {
		return game;
	}

	public int getID() {
		return id;
	}

	@Override
	public final Lobby getLobby() {
		return lobby;
	}

	@Override
	public File getLogDir() {
		File homeDir = container.getHomeDir();
		String logDir = container.getLogDir();
		File file = new File(logDir);
		if (!file.isAbsolute())
			file = new File(homeDir, logDir);

		file = new File(file, "rooms");
		file = new File(file, "tables");

		return file;
	}

	public final int getMaxSeatCount() {
		return playerGZIDs.size();
	}

	public int getNumber() {
		return number;
	}

	public Seat getSeat(Connection user) {
		synchronized (seats) {
			int seatIndex = getSeatIndex(user);
			if (seatIndex == -1)
				return null;

			return seats[seatIndex];
		}
	}

	public int getSeatCount() {
		synchronized (seats) {
			int result = 0;
			for (int i = 0; i < seats.length; i++)
				if (seats[i] != null)
					result++;

			return result;
		}
	}

	public int getSeatFlags() {
		synchronized (seats) {
			int result = 0;
			for (int i = 0; i < seats.length; i++)
				if (seats[i] != null)
					result |= 1 << i;

			return result;
		}
	}

	public int getSeatIndex(Connection user) {
		synchronized (seats) {
			for (int i = 0; i < seats.length; i++) {
				if (seats[i] == null)
					continue;

				String gzid = user != null ? user.getGZID() : "COMPUTER";
				String gzid1 = seats[i].getConnection() != null ? seats[i].getConnection().getGZID() : "COMPUTER";
				if (seats[i] != null && gzid.equalsIgnoreCase(gzid1))
					return i;
			}

			return -1;
		}
	}

	protected void getSeatsWithSameIP(List<Seat> seats) {
		HashMap<String, List<String>> ips = new HashMap<>();
		for (Seat seat : seats) {
			if (seat.getConnection() == null)
				continue;

			String ip = seat.getConnection().getIP().getHostAddress();
			List<String> players = ips.get(ip);
			if (players == null) {
				players = new ArrayList<>();
				ips.put(ip, players);
			}

			players.add(seat.getConnection().getGZID());
		}

		Set<String> keys = ips.keySet();
		for (String ip : keys) {
			List<String> players = ips.get(ip);
			if (players.size() > 1) {
				String message = "The players " + players.get(0);
				for (int i = 1; i < players.size(); i++)
					message += ", " + players.get(i);

				logToOut(message + " have the same ip " + ip);
			}
		}
	}

	public long getStartTime() {
		return startTime;
	}

	public int getTime() {
		return game.getCurrentTimePerTurn() - TIME_PER_MOVE_TOLERANCE;
	}

	public int getTimePerMove() {
		return timePerMove;
	}

	protected abstract String getTurnStr(int turn);

	public final String getVariant() {
		return variant;
	}

	protected final int getWinner() {
		int winnerBits = game.getWinnerBits();
		if (winnerBits == 0)
			return -1;

		return BitUtil.lowBit(winnerBits);
	}

	@Override
	protected void handleDataException(Connection user, String opcode, GZStruct request, Throwable e) {
		if (!(e instanceof IOException))
			rated = false;

		super.handleDataException(user, opcode, request, e);
		dumpGameState();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (game == null ? 0 : game.hashCode());
		result = prime * result + id;
		result = prime * result + (lobby == null ? 0 : lobby.hashCode());
		return result;
	}

	public boolean isNoWatches() {
		return noWatches;
	}

	public boolean isPlayersTheSame() {
		List<Seat> seats = seats(true);
		if (seats.size() == 1)
			return false;

		Seat first = null;
		for (Seat seat : seats)
			if (first == null)
				first = seat;
			else {
				Connection firstConnection = first.getConnection();
				Connection connection = seat.getConnection();
				if (firstConnection != null && connection != null && (firstConnection.getIP().equals(connection.getIP()) || firstConnection.getCompID().equals(connection.getCompID())))
					return true;
			}

		return false;
	}

	public boolean isRated() {
		return rated;
	}

	public boolean isSinglePlayer() {
		return false;
	}

	private boolean isSomePlayerSystem() {
		for (Seat seat : seats)
			if (seat.user != null && seat.user.isSystem())
				return true;

		return false;
	}

	protected void notifyGameSetup(Connection connection) {
		GZStruct response = new GZStruct();
		notifyGameSetup(connection, response);
		connection.postBlock(response);
	}

	private void notifyGameSetup(Connection connection, GZStruct response) {
		GZStruct gd = new GZStruct();
		int seatIndex = getSeatIndex(connection);
		gd.setBoolean("rated", rated);
		gd.setString("tu", getTurnStr(seatIndex));
		gd.setInt("sbtm", (int) (System.currentTimeMillis() - startTime) / 1000);

		if (matchTime > 0)
			gd.setInt("mt", matchTime);

		if (timePerMove > 0) {
			gd.setInt("tmdl", timePerMove);

			int time = game.getCurrentTimePerTurn() - TIME_PER_MOVE_TOLERANCE;
			gd.setInt("ctmv", time > 0 ? time : 0);
		}

		for (int i = 0; i < seats.length; i++) {
			Seat seat = seats[i];
			if (seat == null)
				continue;

			Connection user = seat.getConnection();
			if (user != null) {
				String s = user.getStars() + "_" + user.getNick();
				gd.setString("ur" + (i + 1), s);
			} else
				gd.setString("ur" + (i + 1), "0_Computer");

			int time = seats[i].getPlayer().getCurrentTime() - MATCH_TIME_TOLERANCE;

			if (matchTime > 0)
				gd.setInt("prt" + (i + 1), time > 0 ? time : 0);
			
			gd.setBoolean("fc" + (seat.getIndex() + 1), seat.isFocused());
		}

		notifyGameState(connection, gd);

		if (!game.isRunning()) {
			int winner = getWinner();
			gd.setInt("win", winner != -1 ? winner + 1 : 3);
		}

		int currentTurn = game.getCurrentTurn();
		if (currentTurn != MultiplayerGame.NO_TURN)
			gd.setInt("pe", currentTurn + 1);

		response.setStruct("gd", gd);
	}

	protected abstract void notifyGameState(Connection connection, GZStruct gd);

	@Override
	protected void onConnectionAdded(Connection user, GZStruct response) {
		super.onConnectionAdded(user, response);

		boolean isPlayer = false;
		for (int i = 0; i < playerGZIDs.size(); i++) {
			String player = playerGZIDs.get(i);
			if (user.getGZID().equals(player)) {
				isPlayer = true;
				sitInternal(i, user);
			}
		}

		if (!isPlayer) {
			watchers.add(user);
			if (!gameSetup)
				notifyGameSetup(user, response);
		} else if (gameSetup && getSeatCount() == seats.length) {
			if (seats.length == 2 && seats[0].user != null && seats[1].user != null && seats[0] != seats[1]) {
				seats[0].wins = container.getWinsAgainst(seats[0].user, seats[1].user, DEFAULT_WINS_QUERY_PERIOD_SEC);
				seats[0].losses = container.getLossesAgainst(seats[0].user, seats[1].user, DEFAULT_WINS_QUERY_PERIOD_SEC);
				seats[1].wins = container.getWinsAgainst(seats[1].user, seats[0].user, DEFAULT_WINS_QUERY_PERIOD_SEC);
				seats[1].losses = container.getLossesAgainst(seats[1].user, seats[0].user, DEFAULT_WINS_QUERY_PERIOD_SEC);
			}

			startGame(seats[0], seats());
		}
	}

	private void onContinueGameTimeOut(Question<Seat, Seat> question, List<Seat> accepteds) {
		post(() -> {
			if (continueGameQuestion == null)
				return;

			continueGameQuestion.close();

			if (accepteds.size() > 1)
				startGame(null, accepteds);
			else
				onStartGameCanceled(null, NO_ENOUGH_PLAYERS);
		});
	}

	protected void onSeatCantStart(Seat seat) {

	}

	protected void onStartGame() {
		tmrCheckStarted.pause();

		startTime = System.currentTimeMillis();

		List<Connection> users = users();
		for (Connection user : users)
			notifyGameSetup(user);

		gameSetup = false;
	}

	protected void onStartGameCanceled(Seat starter, int reason) {
		for (Seat seat : seats)
			if (seat != null)
				seat.wantToStart = false;

		if (reason == NO_ENOUGH_PLAYERS && starter != null) {
		}
	}

	private void onStartGameTimeOut(Question<Seat, Seat> question, List<Seat> accepteds) {
		post(() -> {
			if (startGameQuestion == null)
				return;

			Seat questioner;
			synchronized (startGameQuestion) {
				if (!startGameQuestion.isOpen())
					return;

				questioner = startGameQuestion.getQuestioner();
				startGameQuestion.close();
			}

			if (accepteds.size() > 0) {
				accepteds.add(questioner);
				startGame(questioner, accepteds);
			} else
				onStartGameCanceled(questioner, NO_ENOUGH_PLAYERS);
		});
	}

	protected void onStopGame(StopReason reason) {
		boolean logStats = reason != StopReason.CANCELED && rated && isReachedTheMinimumConditionalToRated() && System.currentTimeMillis() - getStartTime() >= MINIMUM_TIME_TO_RATED
				&& (isSomePlayerSystem() || !isPlayersTheSame());
		if (logStats) {
			system.cleanup();

			int counter = 0;
			for (Seat seat : seats) {
				if (seat == null)
					continue;

				Connection user = seat.getConnection();
				if (user == null)
					continue;

				Player player = seat.getPlayer();
				if (player == null)
					continue;

				seat.oldRating = user.getRating(variant);
				seat.oldRating2 = user.getRating2(variant);
				system.addRating(seat, seat.oldRating2);

				if (player.isWinner())
					system.setWinner(counter);

				counter++;
			}

			system.compute();

			int[] sitIndexes = new int[system.count()];
			String[] gzids = new String[system.count()];
			int[] ratings = new int[system.count()];
			for (int index = 0; index < system.count(); index++) {
				Seat seat = system.getPlayer(index);
				Connection user = seat.getConnection();
				Player player = seat.getPlayer();

				int rating = Math.round(system.getNewRating(index));
				int gain = rating - seat.oldRating2;

				sitIndexes[index] = seat.getIndex();
				gzids[index] = user.getGZID();
				ratings[index] = rating;
				int divisor1 = seat.wins > 1 ? seat.wins : 1;
				int divisor2 = seat.wins - seat.losses > 1 ? seat.wins - seat.losses : 1;

				if (player.isWinner()) {
					seat.wins++;
					user.incrementWins(getVariant(), RATING_GAIN / divisor1, seat.oldRating2 + gain / divisor2);
				} else if (player.isLoser()) {
					seat.losses++;
					user.incrementLosses(getVariant(), player.isAbandoned(), 0, seat.oldRating2 + gain);
				} else if (player.isDraw())
					user.incrementDraws(getVariant(), 0, gain > 0 ? seat.oldRating2 + gain / divisor2 : seat.oldRating2 + gain);
				else
					throw new RuntimeException("Invalid player result " + player.getResult());
			}
		}

		for (Seat seat : seats)
			if (seat != null)
				seat.wantToStart = false;

		logToOut("The game #" + game.getGameNumber() + " has " + (reason == StopReason.CANCELED ? "canceled" : "stoped"));

		int winner = getWinner();

		if (reason != StopReason.CANCELED) {
			String seat0Nick = seats[0].getConnection() != null ? seats[0].getConnection().getNick() : "Computer";
			String seat0GZID = seats[0].getConnection() != null ? seats[0].getConnection().getGZID() : "COMPUTER";
			int seat0Rating = seats[0].getConnection() != null ? seats[0].getConnection().getRating(variant) : 0;

			String seat1Nick = seats[1].getConnection() != null ? seats[1].getConnection().getNick() : "Computer";
			String seat1GZID = seats[1].getConnection() != null ? seats[1].getConnection().getGZID() : "COMPUTER";
			int seat1Rating = seats[1].getConnection() != null ? seats[1].getConnection().getRating(variant) : 0;

			if (winner == -1)
				logToOut(new String[] { "Result: Draw", "\tPlayer 1:", "\t\tGZID: " + seat1GZID, "\t\tNick: " + seat1Nick, "\t\tOld rating: " + seats[1].oldRating, "\t\tNew rating: " + seat1Rating,
						"\tPlayer 2: ", "\t\tGZID: " + seat0GZID, "\t\tNick: " + seat0Nick, "\t\tOld rating: " + seats[0].oldRating, "\t\tNew rating: " + seat0Rating

				});
			else
				logToOut(new String[] { "Result: " + (winner == 0 ? "0-1" : "1-0"), "\tPlayer 1:", "\t\tGZID: " + seat1GZID, "\t\tNick: " + seat1Nick, "\t\tOld rating: " + seats[1].oldRating,
						"\t\tNew rating: " + seat1Rating, "\tPlayer 2: ", "\t\tGZID: " + seat0GZID, "\t\tNick: " + seat0Nick, "\t\tOld rating: " + seats[0].oldRating,
						"\t\tNew rating: " + seat0Rating });
		}

		List<Connection> users = users();
		for (Connection user : users) {
			int seatIndex = getSeatIndex(user);
			GZStruct response = new GZStruct();
			GZStruct gd = new GZStruct();
			gd.setInt("win", winner != -1 ? winner + 1 : 3);
			gd.setBoolean("me", seatIndex != -1 && winner != -1 && seatIndex == winner);
			gd.setBoolean("re", isSinglePlayer() ? true : seatIndex == 0);
			response.setStruct("gd", gd);
			user.postBlock(response);
		}

		if (logStats)
			container.logGameResult(getVariant(), getLobby(), true, seats[0].getConnection(), seats[0].oldRating, seats[0].oldRating2, seats[1].getConnection(), seats[1].oldRating,
					seats[1].oldRating2, startTime, getTurnCount(), winner);

		if (isOpen() && reason != StopReason.CANCELED && getSeatCount() > 1) {
			List<Seat> questioneds = seatsThatCanStart();
			if (questioneds.size() > 1) {
				getSeatsWithSameIP(questioneds);
				continueGameQuestion.open(null, questioneds, Integer.MAX_VALUE, (question, accepteds) -> onContinueGameTimeOut(question, accepteds));
			}
		}
	}

	protected abstract int getTurnCount();

	protected void open(Container server, Lobby lobby, int number, String variant, boolean allowWatchers, int[] options, Connection... players) throws IOException {
		this.lobby = lobby;
		this.number = number;
		this.variant = variant;
		this.allowWatchers = allowWatchers;

		playerGZIDs = new ArrayList<>();
		playerNicks = new ArrayList<>();

		rated = true;

		try {
			ArrayList<Integer> computerPlayers = new ArrayList<>();
			for (int i = 0; i < players.length; i++) {
				Connection player = players[i];
				if (player == null) {
					rated = false;
					playerGZIDs.add("COMPUTER");
					playerNicks.add("Computer");
					computerPlayers.add(i);
				} else {
					if (playerGZIDs.contains(player.getGZID()))
						rated = false;

					playerGZIDs.add(player.getGZID());
					playerNicks.add(player.getNick());
				}
			}

			parseOptions(options);

			gameSetup = true;

			id = ID.getAndUpdate((operand) -> {
				operand++;
				if (operand == 0)
					operand = 1000;

				return operand;
			});

			super.open(lobby.getGroup(), server);

			seats = createSeats();
			watchers = new Vector<>();
			game = createGame();

			game.setUseTime(true);
			game.setMinTime(MATCH_TIMES[0] * 60);
			game.setMaxTime(MATCH_TIMES[MATCH_TIMES.length - 1] * 60);

			if (matchTime > 0)
				game.setInitialTime(matchTime * 60 + MATCH_TIME_TOLERANCE);
			else
				game.setUseTime(false);

			game.setUseIncrementTime(false);

			game.setUseTimePerTurn(true);
			game.setMinTimePerTurn(TIMES_PER_MOVE[0]);
			game.setMaxTimePerTurn(TIMES_PER_MOVE[TIMES_PER_MOVE.length - 1] + TIME_PER_MOVE_TOLERANCE);

			if (timePerMove > 0)
				game.setTimePerTurn(timePerMove + TIME_PER_MOVE_TOLERANCE);
			else
				game.setUseTimePerTurn(false);

			startGameQuestion = new SeatQuestion();
			continueGameQuestion = new SeatQuestion();

			defaultConfig();

			system = new RatingSystem<>();
			system.setMinRating(server.getMinRating());
			system.setMaxRating(server.getMaxRating());

			getQueue().setName("Table queue [" + lobby.getName() + "-" + number + "]");

			for (int index : computerPlayers)
				sit(index, null);
		} finally {
			tmrCheckStarted = new Timer(getQueue(), "tmrCheckStarted", CHECK_FIRST_GAME_STARTED_INTERVAL, true, (e) -> container.handleException(e));
			tmrCheckStarted.addListener((timer, interval) -> checkFirstGameStarted());
			tmrCheckStarted.play();
		}
	}

	private void checkFirstGameStarted() {
		tmrCheckStarted.pause();

		if (!game.isRunning())
			close();
	}

	@Override
	protected void parseDataInternal(Connection user, String opcode, GZStruct request) throws Throwable {
		switch (opcode) {
			case "gp": {
				String gameOpcode = request.getString("f");
				parseGameData(user, gameOpcode, request);
				break;
			}

			default:
				super.parseDataInternal(user, opcode, request);
		}
	}

	protected void parseGameData(Connection user, String opcode, GZStruct request) throws Throwable {
		switch (opcode) {
			case "fc": { // focus
				Seat seat = getSeat(user);
				if (seat == null)
					break;
				
				seat.setFocused(true);
				
				GZStruct gd = new GZStruct();
				gd.setBoolean("fc" + (seat.getIndex() + 1), true);
				
				GZStruct response = new GZStruct();
				response.setStruct("gd", gd);
				new BroadCast(response).send();
				
				break;
			}
			
			case "bl": { // blur
				Seat seat = getSeat(user);
				if (seat == null)
					break;
				
				seat.setFocused(false);
				
				GZStruct gd = new GZStruct();
				gd.setBoolean("fc" + (seat.getIndex() + 1), false);
				
				GZStruct response = new GZStruct();
				response.setStruct("gd", gd);
				new BroadCast(response).send();
				
				break;
			}
			
			case "replay": {
				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (game.isRunning())
					break;

				if (!continueGameQuestion.isOpen())
					break;

				for (int i = 0; i < continueGameQuestion.getQuestionedCount(); i++) {
					Seat questioned = continueGameQuestion.getQuestioned(i);
					acceptStartGame(questioned);
				}

				break;
			}
		}
	}

	protected void parseOptions(int[] options) {
		if (getMaxSeatCount() > 1) {
			matchTime = options[0];
			timePerMove = options[1];
		} else {
			matchTime = 0;
			timePerMove = DEFAULT_TIME_PER_TURN_SEC + TIME_PER_MOVE_TOLERANCE;
		}
	}

	public List<String> playerGZIDs() {
		return new ArrayList<>(playerGZIDs);
	}

	public List<String> playerNicks() {
		return new ArrayList<>(playerNicks);
	}

	public List<Player> players() {
		synchronized (seats) {
			ArrayList<Player> result = new ArrayList<>();
			for (Seat seat : seats)
				if (seat != null && seat.getPlayer() != null)
					result.add(seat.getPlayer());

			return result;
		}
	}

	public List<Seat> playingSeats() {
		synchronized (seats) {
			ArrayList<Seat> result = new ArrayList<>();
			for (Seat seat : seats)
				if (seat != null && seat.isPlaying())
					result.add(seat);

			return result;
		}
	}

	protected void rejectStartGame(Seat seat) {
		seat.wantToStart = false;

		if (continueGameQuestion == null)
			return;

		synchronized (continueGameQuestion) {
			if (continueGameQuestion.isOpen()) {
				if (!continueGameQuestion.reject(seat))
					return;

				if (continueGameQuestion.getRejectedCount() == continueGameQuestion.getQuestionedCount() - 1) {
					continueGameQuestion.close();

					onStartGameCanceled(null, NO_REASON);
				} else if (continueGameQuestion.allOpined() && continueGameQuestion.getAcceptedCount() > 1) {
					List<Seat> accepteds = new ArrayList<>();
					for (int i = 0; i < continueGameQuestion.getQuestionedCount(); i++)
						accepteds.add(continueGameQuestion.getQuestioned(i));

					continueGameQuestion.close();

					startGame(null, accepteds);
				}

				return;
			}
		}

		if (startGameQuestion == null)
			return;

		synchronized (startGameQuestion) {
			if (!startGameQuestion.isOpen())
				return;

			if (seat.equals(startGameQuestion.getQuestioner())) {
				startGameCanceled();

				return;
			}

			if (!startGameQuestion.reject(seat))
				return;

			if (startGameQuestion.allOpined() && startGameQuestion.getAcceptedCount() > 0) {
				Seat questioner = startGameQuestion.getQuestioner();
				List<Seat> accepteds = new ArrayList<>();
				accepteds.add(startGameQuestion.getQuestioner());
				for (int i = 0; i < startGameQuestion.getQuestionedCount(); i++)
					accepteds.add(startGameQuestion.getQuestioned(i));

				startGameQuestion.close();

				startGame(questioner, accepteds);
			} else if (startGameQuestion.allRejected())
				startGameCanceled();
		}
	}

	@Override
	protected boolean removeConnectionInternal(Connection user, boolean normalExit) {
		if (!super.removeConnectionInternal(user, normalExit))
			return false;

		logToOut("User " + user.getGZID() + " has left from the game" + (!normalExit ? " and is trying to reconnecting" : ""));

		if (!watchers.remove(user))
			synchronized (seats) {
				for (int i = 0; i < seats.length; i++) {
					if (seats[i] == null)
						continue;

					Seat seat = seats[i];
					synchronized (seat) {
						if (user.equals(seat.getConnection())) {
							standUpInternal(i, true);

							GZStruct response = new GZStruct();
							response.setString("c", "qt");
							response.setString("by", user.getNick());
							new BroadCast(response, user).send();

							schedule(() -> close(), 1000);
							break;
						}
					}
				}
			}

		return true;
	}

	protected synchronized void rollBackConfig() {
		privacy = oldPrivacy;
		noWatches = oldNoWatches;
	}

	protected synchronized void saveConfig() {
		oldPrivacy = privacy;
		oldNoWatches = noWatches;
	}

	public List<Seat> seats() {
		return seats(false);
	}

	public List<Seat> seats(boolean includeReconnecting) {
		ArrayList<Seat> result = new ArrayList<>();

		synchronized (seats) {
			for (Seat seat : seats)
				if (seat != null && !result.contains(seat))
					result.add(seat);
		}

		return result;
	}

	public List<Seat> seatsThatCanStart() {
		ArrayList<Seat> result = new ArrayList<>();
		synchronized (seats) {
			for (Seat seat : seats)
				if (seat != null && seat.canStart() && !result.contains(seat))
					result.add(seat);

			return result;
		}
	}

	public final boolean sit(int seatIndex, Connection user) {
		return send((ReturnableProcess<Boolean>) () -> sitInternal(seatIndex, user));
	}

	public int sitCount() {
		int result = 0;
		synchronized (seats) {
			for (Seat seat : seats)
				if (seat != null)
					result++;
		}

		return result;
	}

	protected boolean sitInternal(int seatIndex, Connection user) {
		synchronized (seats) {
			if (seatIndex < 0 || seatIndex >= seats.length || seats[seatIndex] != null)
				return false;

			// if (getSeatIndex(user) != -1)
			// return false;

			seats[seatIndex] = createSeat(seatIndex, user);
			watchers.remove(user);

			logToOut("User " + (user != null ? user.getGZID() : "COMPUTER") + " sit on the seat #" + seatIndex);
			if (DEBUG && DebugUtil.DEBUG_MODE)
				System.out.println("The user " + (user != null ? user.getGZID() : "COMPUTER") + " sit in the seat " + seatIndex + " and received the descriptor: " + seats[seatIndex]);

			return true;
		}
	}

	protected void standUpInternal(int seatIndex, boolean removing) {
		synchronized (seats) {
			Seat seat = seats[seatIndex];
			if (seat == null)
				return;

			synchronized (seat) {
				Connection user = seat.getConnection();

				logToOut("User " + (user != null ? user.getGZID() : "COMPUTER") + " has stand up from the seat #" + seatIndex);

				rejectStartGame(seat);

				seat.leave();
				seats[seatIndex] = null;

				if (!removing)
					watchers.add(user);
			}
		}
	}

	public List<Seat> startedButNotPlayingSeats() {
		synchronized (seats) {
			ArrayList<Seat> result = new ArrayList<>();
			for (Seat seat : seats)
				if (seat != null && seat.canStart() && seat.wantToStart() && !result.contains(seat))
					result.add(seat);

			return result;
		}
	}

	public List<Seat> startedSeats() {
		synchronized (seats) {
			ArrayList<Seat> result = new ArrayList<>();
			for (Seat seat : seats)
				if (seat != null) {
					Player player = seat.getPlayer();
					if (player != null && player.isStarted() && !result.contains(seat))
						result.add(seat);
				}

			return result;
		}
	}

	protected final void startGame(Seat starter, List<Seat> seats) {
		synchronized (this) {
			if (isClosing() || isClosed())
				return;
		}

		if (game.isRunning())
			return;

		assert startGameQuestion != null && !startGameQuestion.isOpen();

		synchronized (seats) {
			for (Seat seat : seats)
				if (!seat.start())
					onSeatCantStart(seat);
		}

		synchronized (game) {
			if (game.getStartedCount() > 1) {
				if (!game.start())
					onStartGameCanceled(starter, NO_REASON);
			} else
				onStartGameCanceled(starter, NO_ENOUGH_PLAYERS);
		}
	}

	private void startGameCanceled() {
		int count;
		Seat questioned;
		Seat questioner;
		synchronized (startGameQuestion) {
			count = startGameQuestion.getQuestionedCount();
			questioned = startGameQuestion.getQuestioned(0);
			questioner = startGameQuestion.getQuestioner();

			startGameQuestion.close();
		}

		onStartGameCanceled(questioner, NO_REASON);
	}

	protected void suggestStartGame(Seat seat) {
		synchronized (this) {
			if (isClosing() || isClosed())
				return;
		}

		if (continueGameQuestion == null)
			return;

		synchronized (continueGameQuestion) {
			if (continueGameQuestion.isOpen()) {
				acceptStartGame(seat);
				return;
			}
		}

		synchronized (startGameQuestion) {
			if (startGameQuestion.isOpen()) {
				acceptStartGame(seat);
				return;
			}

			if (!seat.canStart())
				return;

			for (Seat seat1 : seats)
				if (seat1 != null)
					seat1.wantToStart = false;

			seat.wantToStart = true;

			List<Seat> questioneds = seatsThatCanStart();
			getSeatsWithSameIP(questioneds);
			questioneds.remove(seat);
			if (questioneds.size() == 0) {
				onStartGameCanceled(seat, NO_ENOUGH_PLAYERS);
				return;
			}

			startGameQuestion.open(seat, questioneds, (question, accepteds) -> onStartGameTimeOut(question, accepteds));
		}
	}

	protected void suggestStopGame(Seat seat) {
		synchronized (this) {
			if (isClosing() || isClosed())
				return;
		}
	}

	@Override
	public String toString() {
		return "game " + id + (lobby != null ? " in room " + lobby.getName() : "");
	}

	public List<Connection> watchers() {
		synchronized (watchers) {
			return new ArrayList<>(watchers);
		}
	}

	protected boolean isReachedTheMinimumConditionalToRated() {
		return true;
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
					new Object[] { null, container.getGameName(), getID(), id, sender, sn, receiver, msg, new Timestamp(System.currentTimeMillis()) })));
		} catch (SQLException e) {
			container.handleException(e);
		} catch (InterruptedException e) {
		}
	}

}
