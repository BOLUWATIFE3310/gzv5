package gz.server.net;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.List;
import java.util.Vector;

import common.io.Log;
import common.process.Closer;
import common.process.Interruptable;
import common.process.NonReturnableProcessWithoutArg;
import common.process.ProcessQueue;
import common.process.ReturnableProcess;
import common.process.TimeOutException;
import common.process.timer.Timer;
import common.util.DateTimeUtil;
import gz.server.net.Container.BanType;
import gz.server.net.Container.Group;
import gz.util.GZStruct;
import gz.util.GZUtils;

public abstract class Base implements Interruptable {
	
	private static final boolean BLOCK_ON_BAD_WORDS_ENABLED = false;

	protected class BroadCast {

		private GZStruct response;
		private boolean onlyAdmins;
		private Connection ignore;
		private List<Connection> users;

		public BroadCast(GZStruct response) {
			this(response, users(), false, null);
		}

		public BroadCast(GZStruct response, boolean onlyAdmins) {
			this(response, users(), onlyAdmins, null);
		}

		public BroadCast(GZStruct response, boolean onlyAdmins, Connection ignore) {
			this(response, users(), onlyAdmins, ignore);
		}

		public BroadCast(GZStruct response, Connection ignore) {
			this(response, users(), false, ignore);
		}

		public BroadCast(GZStruct response, List<Connection> users) {
			this(response, users, false, null);
		}

		public BroadCast(GZStruct response, List<Connection> users, boolean onlyAdmins) {
			this(response, users, onlyAdmins, null);
		}

		public BroadCast(GZStruct response, List<Connection> users, boolean onlyAdmins, Connection ignore) {
			this.response = response;
			this.users = users;
			this.onlyAdmins = onlyAdmins;
			this.ignore = ignore;
		}

		public BroadCast(GZStruct response, List<Connection> users, Connection ignore) {
			this(response, users, false, ignore);
		}

		public void send() {
			send(true);
		}

		public void send(boolean flush) {
			synchronized (users) {
				for (Connection other : users) {
					if (onlyAdmins && !other.isAdmin(getLobby().getID()))
						continue;

					if (ignore != null && ignore.equals(other))
						continue;

					other.postBlock(response, flush);
				}
			}
		}

	}

	public class ChatMessage {
		private String senderGZID;
		private String nick;
		private String receiver;
		private String message;
		private long when;

		protected ChatMessage(Connection sender, String message) {
			this(sender, null, message);
		}

		protected ChatMessage(Connection sender, String receiver, String message) {
			this(sender.getGZID(), sender.getNick(), receiver, message, System.currentTimeMillis());
		}

		protected ChatMessage(String senderGZID, String nick, String receiver, String message, long when) {
			this.senderGZID = senderGZID;
			this.nick = nick;
			this.receiver = receiver;
			this.message = message;
			this.when = when;
		}

		public String getMessage() {
			return message;
		}

		public String getNick() {
			return nick;
		}

		public String getReceiver() {
			return receiver;
		}

		public String getSenderGZID() {
			return senderGZID;
		}

		public long getWhen() {
			return when;
		}
		
		public GZStruct toStruct(boolean priv) {
			return toStruct(priv, null);
		}

		public GZStruct toStruct(boolean priv, Runnable onBadWord) {
			GZStruct result = new GZStruct();
			result.setString("s", senderGZID + ":" + (priv ? "1" : "0") + ":" + nick);
			result.setString("t", container.isAdmin(getLobby().getID(), senderGZID) ? message : container.filterMessage(message, onBadWord));
			return result;
		}
	}

	protected abstract class Question<U, V> {

		public static final int DEFAULT_TIME_OUT = 10000; // 10 segundos

		public static final int NO_RESPONSE = 0;
		public static final int ACCEPTED = 1;
		public static final int REJECTED = 2;

		private U questioner;
		private List<V> questioneds;
		private QuestionTimeout<U, V> callback;

		private byte[] responses;
		private Timer tmrResponseExpires;
		private boolean closed;

		protected Question() {
			questioner = null;
			questioneds = null;
			callback = null;
			responses = null;
			closed = true;

			tmrResponseExpires = new Timer(getQueue(), DEFAULT_TIME_OUT, true, (e) -> container.logToErr(Base.this.toString(), e));
			tmrResponseExpires.addListener((timer, interval) -> {
				tmrResponseExpires.pause();

				if (Question.this.callback != null)
					Question.this.callback.onTimeout(Question.this, accepteds());
			});
		}

		public synchronized boolean accept(V questioned) {
			if (closed || questioned.equals(questioner))
				return false;

			int index = questioneds.indexOf(questioned);
			if (index == -1)
				return false;

			responses[index] = ACCEPTED;

			return true;
		}

		private synchronized List<V> accepteds() {
			ArrayList<V> result = new ArrayList<>();

			if (closed)
				return result;

			for (int i = 0; i < responses.length; i++)
				if (responses[i] == ACCEPTED)
					result.add(questioneds.get(i));

			return result;
		}

		public synchronized boolean allAccepted() {
			return getAcceptedCount() == getQuestionedCount();
		}

		public synchronized boolean allOpined() {
			return getOpinedCount() == getQuestionedCount();
		}

		public synchronized boolean allRejected() {
			return getRejectedCount() == getQuestionedCount();
		}

		public synchronized void close() {
			if (closed)
				return;

			callback = null;
			questioneds = null;
			questioner = null;
			responses = null;
			tmrResponseExpires.pause();

			closed = true;
		}

		public synchronized int getAcceptedCount() {
			int result = 0;

			for (int i = 0; i < getQuestionedCount(); i++)
				if (getResponse(i) == ACCEPTED)
					result++;

			return result;
		}

		public synchronized int getOpinedCount() {
			int result = 0;

			for (int i = 0; i < getQuestionedCount(); i++)
				if (getResponse(i) != NO_RESPONSE)
					result++;

			return result;
		}

		public synchronized V getQuestioned(int index) {
			return !closed ? questioneds.get(index) : null;
		}

		public synchronized int getQuestionedCount() {
			return !closed ? questioneds.size() : 0;
		}

		public synchronized U getQuestioner() {
			return questioner;
		}

		public synchronized int getRejectedCount() {
			int result = 0;

			for (int i = 0; i < getQuestionedCount(); i++)
				if (getResponse(i) == REJECTED)
					result++;

			return result;
		}

		public synchronized int getResponse(int index) {
			return responses[index];
		}

		public synchronized int indexOfQuestioned(V questioned) {
			return !closed ? questioneds.indexOf(questioned) : -1;
		}

		public synchronized boolean isOpen() {
			return !closed;
		}

		public synchronized void open(U questioner, List<V> questioneds, int timeOut, QuestionTimeout<U, V> callback) {
			if (!closed)
				return;

			this.questioner = questioner;
			this.questioneds = questioneds;
			this.callback = callback;

			closed = false;

			responses = new byte[questioneds.size()];
			for (int i = 0; i < responses.length; i++)
				responses[i] = NO_RESPONSE;

			tmrResponseExpires.setInterval(timeOut);
			tmrResponseExpires.reset();
			tmrResponseExpires.play();
		}

		public synchronized boolean reject(V questioned) {
			if (closed || questioned.equals(questioner))
				return false;

			int index = questioneds.indexOf(questioned);
			if (index == -1)
				return false;

			responses[index] = REJECTED;

			return true;
		}

		public synchronized void terminate() {
			close();

			try {
				tmrResponseExpires.close();
			} finally {
				tmrResponseExpires = null;
			}
		}

		public synchronized boolean wasQuestioned(V questioned) {
			return questioneds.contains(questioned);
		}

	}

	protected interface QuestionTimeout<U, V> {
		void onTimeout(Question<U, V> question, List<V> accepteds);
	}

	public static final int MAX_CHAT_HISTORY = 50;

	public static final int MAX_CHAT_VIEW = 35;

	public static final int MAX_CHAT_MESSAGE_LENGTH = 256;

	protected Container container;

	protected int objectsToClose;
	protected int objectsClosed;
	private Closer closer;

	private ThreadGroup group;
	protected Vector<Connection> connections;
	private ProcessQueue queue;
	protected Vector<ChatMessage> chatLog;
	private int chatCounter;

	private Log log;
	
	private Vector<String> alerts;

	protected Base() {

	}

	public void addChat(ChatMessage chatMessage) {
		synchronized (chatLog) {
			chatLog.add(chatMessage);
			if (chatLog.size() == MAX_CHAT_HISTORY)
				chatLog.remove(0);
		}

		chatCounter++;
	}

	public void addChat(Connection user, String receiver, String message) {
		addChat(new ChatMessage(user, receiver, message));
	}

	public void addChat(String senderGZID, String nick, String receiver, String message, long when) {
		addChat(new ChatMessage(senderGZID, nick, receiver, message, when));
	}

	public interface AddUserCallback {

		void onCallback(boolean result);

	}

	public final void addUser(Connection connection, AddUserCallback callback) {
		post(() -> {
			if (addUserInternal(connection)) {
				logToOut(toString(), "[ENTER] " + connection.getNick() + " (" + connection.getGZID() + ") | " + connection.getCompID() + " | " + connection.getIP().getHostAddress());

				GZStruct response = new GZStruct();
				response.setString("auth", "ok");
				if (connection.isAdmin() || getLobby().getGroupID() == null && connection.isAdmin(getLobby().getID()))
					response.setString("iom", "en");

				onConnectionAdded(connection, response);
				connection.postBlock(response);

				if (callback != null)
					callback.onCallback(true);
			} else if (callback != null)
				callback.onCallback(false);
		});
	}

	protected boolean addUserInternal(Connection user) {
		Connection oldUser = getUserByGZID(user.getGZID());
		if (oldUser != null)
			oldUser.postClose();

		connections.add(user);

		return true;
	}

	protected void afterClose() {
		synchronized (log) {
			if (!log.isClosed()) {
				log.logToOut("Log of " + toString() + " closed.");
				log.close();
			}
		}

		connections.clear();

		closer.stopClosing();

		container = null;
	}

	protected void beforeClose() {
		objectsToClose++;
	}

	@Override
	public final void close() {
		container.close(closer);
	}

	public void close(boolean wait) throws TimeOutException {
		container.close(closer, wait);
	}

	public void close(boolean wait, boolean force) throws TimeOutException {
		container.close(closer, wait, force);
	}

	public void close(boolean wait, int timeout) throws TimeOutException {
		container.close(closer, wait, timeout);
	}

	public void close(boolean wait, int timeout, boolean force) throws TimeOutException {
		container.close(closer, wait, timeout, force);
	}

	private void closeInternal() {
		objectsClosed = 0;
		
		alerts.clear();

		beforeClose();

		List<Connection> connections;
		synchronized (this.connections) {
			for (Connection connection : this.connections)
				objectsToClose += connection.closingMaxScore();

			connections = connections();
		}

		this.connections.clear();

		for (Connection connection : connections) {
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

		Container container = this.container;
		try {
			queue.close(true, true);
		} catch (TimeOutException e) {
			container.logToErr(toString(), "WARNING: The queue was forced closed.");
		} finally {
			objectsClosed++;
		}
	}

	public int closingMaxScore() {
		return objectsToClose;
	}

	public synchronized int closingScore() {
		return objectsClosed;
	}

	public List<Connection> connections() {
		synchronized (connections) {
			return new ArrayList<>(connections);
		}
	}

	public boolean containsUser(Connection user) {
		return containsUser(user.getGZID());
	}

	public boolean containsUser(String gzid) {
		synchronized (connections) {
			for (Connection user : connections)
				if (gzid.equalsIgnoreCase(user.getGZID()))
					return true;

			return false;
		}
	}

	public abstract int gameCount(String user);

	public abstract List<LobbyGame> games(String user);

	public Container getContainer() {
		return container;
	}

	protected ThreadGroup getGroup() {
		return group;
	}

	public abstract Lobby getLobby();

	public abstract File getLogDir();

	public ProcessQueue getQueue() {
		return queue;
	}

	public Connection getUserByFSID(String fsid) {
		synchronized (connections) {
			for (Connection connection : connections)
				if (fsid.equals(connection.getFSID()))
					return connection;
		}

		return null;
	}

	public Connection getUserByGZID(String gzid) {
		synchronized (connections) {
			for (Connection connection : connections)
				if (gzid.equals(connection.getGZID()))
					return connection;
		}

		return null;
	}

	protected void handleDataException(Connection user, String opcode, GZStruct request, Throwable e) {
		container.logToErr(toString(), new String[] { "[user:" + user + "]", "[op:" + opcode + "]", "[req]" + request }, e);
		user.postClose();
	}

	protected void handleException(Throwable e) {
		container.handleException(e);
	}

	@Override
	public void interrupt() {
		group.interrupt();

		container.logToErr(toString(), "WARNING: All threads were interrupted.");
	}

	public final boolean isClosed() {
		return closer == null || closer.isClosed();
	}

	public final boolean isClosing() {
		return closer == null || closer.isClosing();
	}

	public boolean isMuted(Connection user) {
		return container.getMuteExpiresByGZIDOrCompidOrIP(getLobby().getID(), user) != 0;
	}

	public final boolean isOpen() {
		return closer != null && closer.isOpen();
	}

	public void logToOut(String message) {
		logToOut(null, message);
	}

	public void logToOut(String prefix, String message) {
		log.logToOut(prefix, message);
	}

	public void logToOut(String prefix, String[] messages) {
		log.logToOut(prefix, messages);
	}

	public void logToOut(String[] messages) {
		log.logToOut(messages);
	}

	protected void notifyAvatars() {

	}

	protected void onChat(String gzid, String nick, String receiver, String message) {

	}

	protected void onConnectionAdded(Connection connection, GZStruct response) {
		synchronized (connections) {
			GZStruct ul = new GZStruct();
			for (Connection connection1 : connections) {
				if (connection1.equals(connection))
					continue;

				String s = connection1.getOnlineStatus() + ":" + // online status
						connection1.getGZID() + ":" + // gzid
						connection1.getAvatar() + ":" + // avatar
						(connection1.allowShowCountry() ? connection1.getCountry() : "") + ":" + // country
						(connection1.isPremium() ? "1" : "0") + ":" + // premium
						(connection1.isBlocked() ? "1" : "0") + ":" + // blocked
						connection1.getStars() + ":" + // ?
						(connection1.getRating() / 1000) + ":" + // rating value
						connection1.getNick(); // nick
				ul.setString(connection1.getGZID(), s);
			}

			response.setStruct("ul", ul);
		}

		synchronized (chatLog) {
			GZStruct ml = new GZStruct();
			GZStruct tl = new GZStruct();
			for (int i = chatLog.size() - 1; i >= Math.max(chatLog.size() - MAX_CHAT_VIEW, 0); i--) {
				ChatMessage message = chatLog.get(i);
				String sender = message.getSenderGZID();
				String receiver = message.getReceiver();
				if (receiver != null && !receiver.equals(connection.getGZID()) && !sender.equals(connection.getGZID()))
					continue;

				String key = Long.toString(chatCounter - i);
				String params = sender + ":" + (receiver != null ? "1" : "0") + ":" + message.getNick();
				ml.setString(key, params);
				tl.setString(key, container.filterMessage(message.getMessage()));
			}

			response.setStruct("ml", ml);
			response.setStruct("tl", tl);
		}

		GZStruct broadcastResponse = new GZStruct();
		String s = connection.getOnlineStatus() + ":" + // 0
				connection.getGZID() + ":" + // 1
				connection.getAvatar() + ":" + // 2
				(connection.allowShowCountry() ? connection.getCountry() : "") + ":" + // 3
				(connection.isPremium() ? "1" : "0") + ":" + // 4
				(connection.isBlocked() ? "1" : "0") + ":" + // 5
				connection.getStars() + ":" + // 6
				(connection.getRating() / 1000) + ":" + // 7
				connection.getNick(); // 8
		broadcastResponse.setString("au", connection.getGZID() + "_" + s);
		new BroadCast(broadcastResponse, connection).send();
	}
	
	protected void onConnectionRemoved(Connection user) {
		
	}

	protected void open(ThreadGroup group, Container container) throws IOException {
		this.container = container;
		
		alerts = new Vector<>();

		chatCounter = 0;
		objectsToClose = 1;
		closer = new Closer(new Interruptable() {

			@Override
			public void close() {
				closeInternal();
			}

			@Override
			public void interrupt() {
				Base.this.interrupt();
			}

		});

		String s = toString();

		this.group = new ThreadGroup(group, toString());

		connections = new Vector<>();
		chatLog = new Vector<>();

		queue = new ProcessQueue(this.group);
		queue.addExceptionListener((e) -> container.handleException(e));
		queue.addCloseListener(() -> afterClose());

		File file = getLogDir();
		if (!file.exists() && !file.mkdirs())
			throw new IOException("Could not create the log directory " + file.getAbsolutePath());

		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(System.currentTimeMillis());
		String s1 = DateTimeUtil.dateToStr_(calendar);

		log = new Log(new File(file, s + "[" + s1 + "].txt"));
		log.logToOut("Log started for " + s);

		if (!closer.isOpen())
			return;
	}

	public final void parseData(Connection user, String opcode, GZStruct request) {
		if (!closer.isOpen())
			return;

		post(() -> {
			try {
				parseDataInternal(user, opcode, request);
			} catch (Throwable e) {
				handleDataException(user, opcode, request, e);
			}
		});
	}
	
	private void onBadWord(String gzid, GZStruct response) {
		if (BLOCK_ON_BAD_WORDS_ENABLED)
			if (wasAlerted(gzid))
				blockChat(gzid, 2, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), null, "bad words", false);
			else {
				alerts.add(gzid);
				response.setString("alert", "You said a bad word! Dont say it again, next time you will be muted.");
			}
	};

	protected void parseDataInternal(Connection user, String opcode, GZStruct request) throws Throwable {
		switch (opcode) {
			case "m": { // chat message
				if (user.isBlocked())
					return;

				String receiver = request.getString("p");
				String message = request.getString("t");

				if (message.length() > MAX_CHAT_MESSAGE_LENGTH)
					message = message.substring(0, MAX_CHAT_MESSAGE_LENGTH);

				if (message.startsWith("###")) { // system command
					if (!user.isSystem())
						return;

					message = message.substring(3);
					parseSystemCommand(user, message);
					return;
				}

				ChatMessage chatMessage = new ChatMessage(user, receiver, message);
				addChat(chatMessage);

				Connection receiverConnection = null;
				if (receiver != null) {
					receiverConnection = getUserByGZID(receiver);
					if (receiverConnection != null) {
						GZStruct response = new GZStruct();
						response.setStruct("am", chatMessage.toStruct(true));
						receiverConnection.postBlock(response);
					}

					GZStruct response = new GZStruct();
					response.setStruct("am", chatMessage.toStruct(true, () -> onBadWord(user.getGZID(), response)));
					user.postBlock(response);
				} else {
					GZStruct response1 = new GZStruct();
					response1.setStruct("am", chatMessage.toStruct(false, () -> onBadWord(user.getGZID(), response1)));
					user.postBlock(response1);
					
					GZStruct response2 = new GZStruct();
					response2.setStruct("am", chatMessage.toStruct(false, null));
					new BroadCast(response2, user).send();
				}

				logToOut(toString(), "[CHAT] " + user.getNick() + " (" + user.getGZID() + ")"
						+ (receiver != null ? " to " + (receiverConnection != null ? receiverConnection.getNick() + " (" + receiverConnection.getGZID() + ")" : receiver) : "") + ": " + message);

				onChat(user.getGZID(), user.getNick(), receiver, message);

				break;
			}

			case "i": { // info
				String gzid = request.getString("u");
				Connection connection = getUserByGZID(gzid);

				GZStruct response = new GZStruct();
				response.setString("c", "i");
				if (connection != null) {
					String s = gzid + ":" + // 0
							(connection.allowShowEmail() ? connection.getEmail() : "") + ":" + // 1
							(connection.allowShowGenderAndAge() ? Integer.toString(connection.getGender().getValue()) : "") + ":" + // 2
							(connection.allowShowGenderAndAge()
									? String.format("%04d", connection.getBirthYear()) + String.format("%02d", connection.getBirthMonth()) + String.format("%02d", connection.getBirthDay()) : "")
							+ ":" + // 3
							Long.toString(connection.getRegTimestamp().getTime() / 1000) + ":" + // 4
							connection.getStars() + ":" + // 5
							(connection.getRating() / 1000) + ":" + // 6
							connection.getPlayeds() + ":" + // 7
							connection.getWins() + ":" + // 8
							"0" + ":" + // 9
							"0" + ":" + // 10
							"0" + ":" + // 11
							"0" + ":" + // 12
							"0"; // 13
					response.setString("v", s);
				}

				user.postBlock(response);

				break;
			}

			case "s": { // change status
				int s;
				try {
					s = request.getInt("s");
				} catch (NumberFormatException e) {
					user.postClose();
					return;
				}

				if (s < 1 || s > 5)
					return;

				if (!user.isPremium() && (s == 2 || s == 4 || s == 5))
					return;

				user.setOnlineStatus(s);

				GZStruct response = new GZStruct();
				response.setString("c", "s");
				response.setString("s", s + user.getGZID());
				new BroadCast(response, user).send();

				break;
			}

			case "moder": { // modder action
				if (!user.isAdmin(getLobby().getID()))
					return;

				GZStruct response;

				String m = request.getString("m");
				String gzid = request.getString("i");
				Connection receiver = gzid != null ? getUserByGZID(gzid) : null;

				switch (m) {
					case "warn": {
						if (receiver == null) {
							response = new GZStruct();
							response.setString("moderes", "User not found.");
							user.postBlock(response);
							return;
						}

						response = new GZStruct();
						response.setString("alert", "You have been warned by the moderator.");
						receiver.postBlock(response);

						response = new GZStruct();
						response.setString("moderes", "User " + receiver.getNick() + " was warned.");
						user.postBlock(response);

						logToOut(toString(), "[WARN] " + user.getNick() + " (" + user.getGZID() + ")" + " to " + receiver.getNick() + " (" + receiver.getGZID() + ")");

						break;
					}

					case "disconnect": {
						if (receiver == null) {
							response = new GZStruct();
							response.setString("moderes", "User not found.");
							user.postBlock(response);
							return;
						}

						if (receiver.isAdmin(getLobby().getID())) {
							response = new GZStruct();
							response.setString("moderes", "You cant kick an admin.");
							user.postBlock(response);
							return;
						}

						receiver.postClose();

						response = new GZStruct();
						response.setString("moderes", "User " + receiver.getNick() + " was disconnected.");
						user.postBlock(response);

						logToOut(toString(), "[KICK] " + user.getNick() + " (" + user.getGZID() + ")" + " to " + receiver.getNick() + " (" + receiver.getGZID() + ")");

						break;
					}

					case "clearnick2": {
						// TODO Implementar
						if (receiver != null)
							receiver.postClose();

						response = new GZStruct();
						response.setString("moderes", "User " + (receiver != null ? receiver.getNick() + " " : "") + "has nick cleared for 2 days.");
						user.postBlock(response);

						// logToOut(toString(), "[CLEAR NICK 2] " +
						// user.getNick() + " (" + user.getGZID() + ")" + "
						// to " + receiver.getNick() + " (" +
						// receiver.getGZID() + ")");

						break;
					}

					case "clearnick7": {
						// TODO Implementar
						if (receiver != null)
							receiver.postClose();

						response = new GZStruct();
						response.setString("moderes", "User " + (receiver != null ? receiver.getNick() + " " : "") + "has nick cleared for 7 days.");
						user.postBlock(response);

						// logToOut(toString(), "[CLEAR NICK 7] " +
						// user.getNick() + " (" + user.getGZID() + ")" + "
						// to " + receiver.getNick() + " (" +
						// receiver.getGZID() + ")");

						break;
					}

					case "blockchat2": {
						blockChat(gzid, 2, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), user.getGZID(), "none", false);
						break;
					}

					case "blockchat7": {
						blockChat(gzid, 7, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), user.getGZID(), "none", false);
						break;
					}

					case "blockchat15": {
						blockChat(gzid, 15, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), user.getGZID(), "none", false);
						break;
					}

					case "blockip": {
						blockAccess(gzid, 2, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), user.getGZID(), "none", false);
						break;
					}

					case "clearchat": {
						clearChat(user);
						break;
					}
				}

				break;
			}

			case "gac": { // group owner action
				String groupID = getLobby().getGroupID();
				if (groupID == null)
					return;

				Group group = container.getGroupFromID(groupID);

				if (!user.getGZID().equals(group.getOwner()))
					return;

				String[] v = GZUtils.lim_split(request.getString("v"), "_", 2);

				Lobby lobby = getLobby();

				switch (v[0]) {
					case "i": { // atualizar dados
						break;
					}

					case "dchat": { // limpar chat
						clearChat(user);
						break;
					}

					case "disc": { // desconectar todos
						disconnectAll(user, true);
						logToOut(toString(), "[INFO] " + user.getNick() + "(" + user.getGZID() + ") disconnected all players in the lobby.");
						break;
					}

					case "n": { // novas conexões
						String cmd = v[1];

						switch (cmd) {
							case "alw": { // permitidas
								lobby.allowNewConnections();
								break;
							}

							case "dis": { // bloqueadas
								lobby.denyNewConnections();
								break;
							}
						}

						break;
					}

					case "rb": { // resetar
						String cmd = v[1];

						switch (cmd) {
							case "ip": { // ips bloqueados
								lobby.resetBlockedsByIP(user.getGZID());
								break;
							}

							case "as": { // acessos bloqueados
								lobby.resetBlockedsByAccess(user.getGZID());
								break;
							}

							case "ch": { // chats bloqueados
								lobby.resetBlockedsByChat(user.getGZID());
								break;
							}
						}

						break;
					}
				}

				notifyGroupRoomStatus(user);

				break;
			}

			case "gad": { // group user action
				String v = request.getString("v");
				String gzid = request.getString("u");

				switch (v) {
					case "1": { // bloquear ip
						if (blockAccess(gzid, -1, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), user.getGZID(), "none", true))
							getLobby().ipsBlockeds++;

						break;
					}

					case "2": { // bloquear acesso
						if (blockAccess(gzid, -1, EnumSet.of(BanType.GZID), user.getGZID(), "none", true))
							getLobby().accessBlockeds++;

						break;
					}

					case "3": { // bloquear chat por 48 horas
						if (blockChat(gzid, 2, EnumSet.of(BanType.GZID), user.getGZID(), "none", true))
							getLobby().chatBlockeds++;

						break;
					}
				}

				break;
			}
		}
	}

	private boolean wasAlerted(String gzid) {
		return alerts.contains(gzid);
	}

	protected void parseSystemCommand(Connection connection, String command) {
		if (command.startsWith("alert ")) {
			command = command.substring("alert ".length());

			if (command.startsWith("all ")) {
				command = command.substring("all ".length());

				if (command.startsWith("here ")) {
					String msg = command.substring("here ".length()).replace("\\n", "\n");
					alertAllPlayers(msg);
				} else
					container.alertAllPlayers(command.replace("\\n", "\n"));
			}
		} else if (command.startsWith("create ")) {
			command = command.substring("create ".length());

			if (command.startsWith("public lobby ")) {
				command = command.substring("public lobby ".length());

				String[] params = command.split(" ");
				if (params.length < 3) {
					GZStruct response = new GZStruct();
					response.setString("alert", "Insufficient number of parameters.");
					connection.postBlock(response);
					return;
				}

				String name = params[0];
				try {
					name = URLDecoder.decode(name, "utf-8");
				} catch (UnsupportedEncodingException e) {
				}

				String lobbyHost = params[1];
				String s = params[2];
				int lobbyPort;
				try {
					lobbyPort = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					GZStruct response = new GZStruct();
					response.setString("alert", "The port should be a number.");
					connection.postBlock(response);
					return;
				}

				container.addPublicLobby(name, lobbyHost, lobbyPort);
			} else if (command.startsWith("group lobby ")) {
				command = command.substring("group lobby ".length());

				String[] params = command.split(" ");
				if (params.length < 4) {
					GZStruct response = new GZStruct();
					response.setString("alert", "Insufficient number of parameters.");
					connection.postBlock(response);
					return;
				}

				String name = params[0];
				try {
					name = URLDecoder.decode(name, "utf-8");
				} catch (UnsupportedEncodingException e) {
				}

				String groupID = params[1];
				String lobbyHost = params[2];
				String s = params[3];
				int lobbyPort;
				try {
					lobbyPort = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					GZStruct response = new GZStruct();
					response.setString("alert", "The port should be a number.");
					connection.postBlock(response);
					return;
				}

				container.addGroupLobby(name, groupID, lobbyHost, lobbyPort);
			} else if (command.startsWith("tournament lobby ")) {
				command = command.substring("tournament lobby ".length());

				String[] params = command.split(" ");
				if (params.length < 4) {
					GZStruct response = new GZStruct();
					response.setString("alert", "Insufficient number of parameters.");
					connection.postBlock(response);
					return;
				}

				String name = params[0];
				try {
					name = URLDecoder.decode(name, "utf-8");
				} catch (UnsupportedEncodingException e) {
				}

				String tournamentID = params[1];
				String lobbyHost = params[2];
				String s = params[3];
				int lobbyPort;
				try {
					lobbyPort = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					GZStruct response = new GZStruct();
					response.setString("alert", "The port should be a number.");
					connection.postBlock(response);
					return;
				}

				container.addTournamentLobby(name, tournamentID, lobbyHost, lobbyPort);
			} else if (command.startsWith("national lobby ")) {
				command = command.substring("national lobby ".length());

				String[] params = command.split(" ");
				if (params.length < 4) {
					GZStruct response = new GZStruct();
					response.setString("alert", "Insufficient number of parameters.");
					connection.postBlock(response);
					return;
				}

				String name = params[0];
				try {
					name = URLDecoder.decode(name, "utf-8");
				} catch (UnsupportedEncodingException e) {
				}

				String country = params[1];
				String lobbyHost = params[2];
				String s = params[3];
				int lobbyPort;
				try {
					lobbyPort = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					GZStruct response = new GZStruct();
					response.setString("alert", "The port should be a number.");
					connection.postBlock(response);
					return;
				}

				container.addNationalLobby(name, country, lobbyHost, lobbyPort);
			} else if (command.startsWith("bytype lobby ")) {
				command = command.substring("bytype lobby ".length());

				String[] params = command.split(" ");
				if (params.length < 4) {
					GZStruct response = new GZStruct();
					response.setString("alert", "Insufficient number of parameters.");
					connection.postBlock(response);
					return;
				}

				String name = params[0];
				try {
					name = URLDecoder.decode(name, "utf-8");
				} catch (UnsupportedEncodingException e) {
				}

				String variant = params[1];
				String lobbyHost = params[2];
				String s = params[3];
				int lobbyPort;
				try {
					lobbyPort = Integer.parseInt(s);
				} catch (NumberFormatException e) {
					GZStruct response = new GZStruct();
					response.setString("alert", "The port should be a number.");
					connection.postBlock(response);
					return;
				}

				container.addLobbyByType(name, variant, lobbyHost, lobbyPort);
			} else {
				GZStruct response = new GZStruct();
				response.setString("alert", "Incorrect syntax near '" + command + "'.");
				connection.postBlock(response);
			}
		} else if (command.startsWith("open lobby ")) {
			command = command.substring("open lobby ".length());

			int id;
			try {
				id = Integer.parseInt(command);
			} catch (NumberFormatException e) {
				GZStruct response = new GZStruct();
				response.setString("alert", "The lobby id should be a number.");
				connection.postBlock(response);
				return;
			}

			container.activateLobby(id);
		} else if (command.startsWith("close lobby ")) {
			command = command.substring("close lobby ".length());

			int id;
			try {
				id = Integer.parseInt(command);
			} catch (NumberFormatException e) {
				GZStruct response = new GZStruct();
				response.setString("alert", "The lobby id should be a number.");
				connection.postBlock(response);
				return;
			}

			container.deactivateLobby(id);
		} else {
			GZStruct response = new GZStruct();
			response.setString("alert", "Invalid command '" + command + "'.");
			connection.postBlock(response);
		}
	}

	public boolean blockChat(String gzid, int days, EnumSet<BanType> type, String adminGZID, String reason, boolean onlyHere) {
		Connection blocked = getUserByGZID(gzid);
		Connection admin = adminGZID != null ? getUserByGZID(adminGZID) : null;

		if (container.isAdmin(getLobby().getID(), gzid)) {
			if (admin != null) {
				GZStruct response = new GZStruct();
				response.setString("moderes", "You cant mute an admin.");
				admin.postBlock(response);
			}

			return false;
		}

		container.mute(onlyHere ? getLobby().getID() : -1, gzid, days != -1 ? days * 24 * 60 * 60 * 1000 : -1, type, adminGZID, reason);

		GZStruct response;

		if (blocked != null) {
			response = new GZStruct();

			if (onlyHere)
				response.setString("c", "cban");
			else
				response.setString("c", "cbanc");

			blocked.postBlock(response);

			response = new GZStruct();
			response.setString("c", "zbch");
			response.setString("u", gzid);
			new BroadCast(response, blocked).send();
		}

		response = new GZStruct();
		response.setString("moderes", "User " + (blocked != null ? blocked.getNick() + " " : "") + "has hist chat blocked " + (days != -1 ? "for " + days + " days" : "forever") + ".");

		if (admin != null)
			admin.postBlock(response);

		logToOut(toString(), "[MUTE] " + (admin != null ? admin.getNick() + " (" + admin.getGZID() : "(") + (adminGZID != null ? adminGZID : "SYSTEM") + ") to " + (blocked != null ? blocked.getNick() + " (" : "(") + gzid + ") "
				+ (days != -1 ? "for " + days + " days" : "forever"));

		return true;
	}

	public boolean blockAccess(String gzid, int days, EnumSet<BanType> type, String adminGZID, String reason, boolean onlyHere) {
		Connection blocked = getUserByGZID(gzid);
		Connection admin = adminGZID != null ? getUserByGZID(adminGZID) : null;

		if (container.isAdmin(getLobby().getID(), gzid)) {
			if (admin != null) {
				GZStruct response = new GZStruct();
				response.setString("moderes", "You cant ban an admin.");
				admin.postBlock(response);
			}

			return false;
		}

		container.ban(onlyHere ? getLobby().getID() : -1, gzid, days != -1 ? days * 24 * 60 * 60 * 1000 : -1, type, adminGZID, reason);

		GZStruct response;

		if (blocked != null)
			blocked.postClose();

		response = new GZStruct();
		response.setString("moderes", "User " + (blocked != null ? blocked.getNick() + " " : "") + "was banned " + (days != -1 ? "for " + days + " days" : "forever") + ".");

		if (admin != null)
			admin.postBlock(response);

		logToOut(toString(), "[BLOCK] " + (admin != null ? admin.getNick() + " (" + admin.getGZID() : "(") + (adminGZID != null ? adminGZID : "SYSTEM") + ") to " + (blocked != null ? blocked.getNick() + " (" : "(") + gzid + ") "
				+ (days != -1 ? "for " + days + " days" : "forever"));

		return true;
	}

	public void disconnectAll() {
		disconnectAll(null, true);
	}

	public void disconnectAll(Connection except, boolean exceptAdmins) {
		synchronized (connections) {
			for (Connection connection : connections) {
				if (except != null && connection.getGZID().equals(except.getGZID()))
					continue;

				if (exceptAdmins && connection.isAdmin(getLobby().getID()))
					continue;

				connection.postClose();
			}
		}
	}

	private void clearChat(Connection admin) {
		chatLog.clear();
		GZStruct response = new GZStruct();
		response.setString("c", "rsch");
		new BroadCast(response).send();

		response = new GZStruct();
		response.setString("moderes", "Chat cleared.");
		admin.postBlock(response);

		logToOut(toString(), "[CLEAR CHAT] " + admin.getNick() + " (" + admin.getGZID() + ")");
	}

	private void notifyGroupRoomStatus(Connection user) {
		Lobby lobby = getLobby();

		GZStruct response = new GZStruct();
		response.setString("c", "ia");
		response.setInt("ccu", lobby.userCount());
		response.setInt("b_ip", lobby.getIPsBlockeds());
		response.setInt("b_as", lobby.getAccessBlockeds());
		response.setInt("b_ch", lobby.getChatBlockeds());
		response.setBoolean("alw", lobby.isDenyingNewConnections());
		user.postBlock(response);
	}

	public void alertAllPlayers(String message) {
		GZStruct response = new GZStruct();
		response.setString("alert", message);
		new BroadCast(response).send();
	}

	public void post(NonReturnableProcessWithoutArg process) {
		queue.post(process, (e) -> container.logToErr(Base.this.toString(), e));
	}

	public void postAndWait(NonReturnableProcessWithoutArg process) throws InterruptedException {
		try {
			queue.postAndWait(process);
		} catch (RuntimeException e) {
			container.logToErr(toString(), e);
		}
	}

	public final void removeConnection(Connection connection, boolean normalExit) {
		removeConnection(connection, normalExit, false);
	}

	public final void removeConnection(Connection connection, boolean normalExit, boolean wait) {
		if (closer.isClosing())
			removeConnectionInternal(connection, true);
		else if (wait)
			try {
				postAndWait(() -> removeConnectionInternal(connection, normalExit));
			} catch (InterruptedException e) {
				close();
			}
		else
			post(() -> removeConnectionInternal(connection, normalExit));
	}

	protected boolean removeConnectionInternal(Connection connection, boolean normalExit) {
		if (!connections.remove(connection))
			return false;
		
		onConnectionRemoved(connection);

		GZStruct response = new GZStruct();
		response.setString("ru", connection.getGZID());
		new BroadCast(response).send();

		logToOut(toString(), "[EXIT] " + connection.getNick() + " (" + connection.getGZID() + ")");

		return true;
	}

	public void schedule(NonReturnableProcessWithoutArg process, int time) {
		queue.schedule(process, time, (e) -> container.logToErr(Base.this.toString(), e));
	}

	public void schedule(NonReturnableProcessWithoutArg process, int time, int execCount) {
		queue.schedule(process, time, execCount, (e) -> container.logToErr(Base.this.toString(), e));
	}

	public void send(NonReturnableProcessWithoutArg process) {
		try {
			queue.send(process);
		} catch (InterruptedException e) {
			close();
		} catch (RuntimeException e) {
			container.logToErr(toString(), e);
		}
	}

	public <T> T send(ReturnableProcess<T> process) {
		try {
			return queue.send(process);
		} catch (InterruptedException e) {
			close();
		} catch (Throwable e) {
			container.logToErr(toString(), e);
		}

		return null;
	}

	public int userCount() {
		return connections.size();
	}

	public List<Connection> users() {
		return users(true);
	}

	protected List<Connection> users(boolean copy) {
		if (copy)
			synchronized (connections) {
				return new ArrayList<>(connections);
			}

		return connections;
	}

	public void kick(String gzid) {
		post(() -> kickInternal(gzid));
	}

	protected void kickInternal(String gzid) {
		synchronized (connections) {
			for (Connection connection : connections)
				if (connection.getGZID().equals(gzid))
					connection.postClose();
		}
	}

}
