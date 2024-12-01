package gz.server.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import common.process.Closer;
import common.process.Interruptable;
import common.process.NonReturnableProcessWithoutArg;
import common.process.ProcessQueue;
import common.process.ReturnableProcess;
import common.process.TimeOutException;
import common.process.timer.Timer;
import common.util.DebugUtil;
import gz.net.Gender;
import gz.server.net.Container.Inventory;
import gz.server.net.Container.Session;
import gz.server.net.Container.User;
import gz.server.net.Container.UserStats;
import gz.shared.DBV5Consts;
import gz.util.GZStruct;
import gz.util.GZUtils;

public class Connection implements Interruptable {
	private static final boolean DEBUG_INPUT = true;
	private static final boolean DEBUG_OUTPUT = true;

	static final int TEST_CONNECTION_INTERVAL = 15000; // 15 segundos
	static final int CHECK_CONNECTION_INTERVAL = 60000; // 1 minuto

	public static final int CREATED = 0;
	public static final int OPENING = 1;
	public static final int AUTHENTICATING = 2;
	public static final int OPEN = 3;
	public static final int CLOSING = 4;

	private static final int RECEIVE_CHAT_MAX_MESSAGES = 6;
	private static final int RECEIVE_CHAT_INTERVAL = 5 * 1000; // 5 segundos
	private static final int DEFAULT_MAX_MESSAGES = 25;
	private static final int DEFAULT_INTERVAL = 10 * 1000; // 5 segundos

	private static final int CHAT_FLOOD_MUTE_TIME = 60 * 1000; // 1 minuto
	private static final int CHAT_FLOOD_BAN_TIME = 10 * 60 * 1000; // 10 minutos
	private static final int GENERIC_FLOOD_BAN_TIME = 10 * 60 * 1000; // 10
																		// minutos

	private static final int SPAM_BLOCK_INTERVAL = 10 * 1000;

	@SuppressWarnings("unused")
	private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	@SuppressWarnings("unused")
	private static final int CONNECT = 0;
	private static final int AUTH = 1;
	protected static final int APP = 3;
	private static final int CONNECTED = 8;
	@SuppressWarnings("unused")
	private static final int DATA = 9;
	@SuppressWarnings("unused")
	private static final int DISCONNECTED = 11;
	protected static final int STD = 12;
	public static final int MINIMUN_SENT_INTERVAL = 1000;

	private Lobby lobby;
	private LobbyGame game;
	private Socket socket;

	private Session session;

	private int state;
	private int objectsClosed;
	private boolean reconnecting;
	private Closer closer;
	private InputStream input;
	private OutputStream output;
	private ThreadGroup group;
	private ProcessQueue inputQueue;
	private ProcessQueue outputQueue;
	private Timer tmrTestConnection;
	private Timer tmrCheckConnection;
	private long lastReceivedTime;
	private HashMap<String, List<SpamCheck>> spamCheck;
	private boolean isMutedByFlood;

	protected String remoteKey;
	protected String localKey;
	protected int receivedCount;
	protected int sentCount;
	protected long lastSentTime;
	private String[] VD_DATA;
	@SuppressWarnings("unused")
	private String VD_DATA_STR;
	private String CRK;
	protected String ZF;
	protected int PTSTATUS;
	protected int MODER_STATUS_NT;
	protected int ONLINESTATUS;
	@SuppressWarnings("unused")
	private String zax;

	protected Connection() {

	}

	public boolean allowShowCountry() {
		return session.getUser().isShowingCountry();
	}

	public boolean allowShowEmail() {
		return session.getUser().isShowingGZID();
	}

	public boolean allowShowGenderAndAge() {
		return session.getUser().isShowingGender();
	}

	@Override
	public void close() {
		closeNormal();
	}

	public void close(boolean wait) throws TimeOutException {
		closeNormal(wait);
	}

	public void close(boolean wait, boolean force) throws TimeOutException {
		closeNormal(wait, force);
	}

	public void close(boolean wait, int timeout) throws TimeOutException {
		closeNormal(wait, timeout);
	}

	public void close(boolean wait, int timeout, boolean force) throws TimeOutException {
		closeNormal(wait, timeout, force);
	}

	public void closeAndSetReconnecting() {
		if (!startClosing(false))
			return;

		lobby.getContainer().close(closer);
	}

	public void closeAndSetReconnecting(boolean wait) throws TimeOutException {
		if (!startClosing(false))
			return;

		lobby.getContainer().close(closer, wait);
	}

	public void closeAndSetReconnecting(boolean wait, boolean force) throws TimeOutException {
		if (!startClosing(false))
			return;

		lobby.getContainer().close(closer, wait, force);
	}

	public void closeAndSetReconnecting(boolean wait, int timeout) throws TimeOutException {
		if (!startClosing(false))
			return;

		lobby.getContainer().close(closer, wait, timeout);
	}

	public void closeAndSetReconnecting(boolean wait, int timeout, boolean force) throws TimeOutException {
		if (!startClosing(false))
			return;

		lobby.getContainer().close(closer, wait, timeout, force);
	}

	protected void closeInternal() {
		Base server = game != null ? game : lobby;

		server.removeConnection(this, !reconnecting);

		objectsClosed = 0;

		// server.logToOut(toString(), "Closing output queue...");
		try {
			outputQueue.close(true, true);
		} catch (TimeOutException e) {
			server.getContainer().logToErr(toString(), "WARNING: The output queue was forced closed.");
		} finally {
			// server.logToOut(toString(), "Closing output closed.");
			objectsClosed++;
		}

		// server.logToOut(toString(), "Closing input queue...");
		try {
			inputQueue.close(true, true);
		} catch (TimeOutException e) {
			server.getContainer().logToErr(toString(), "WARNING: The input queue was forced closed.");
		} finally {
			// server.logToOut(toString(), "Closing input closed.");
			objectsClosed++;
		}

		closeObjects();

		// lobby = null;
		// game = null;
	}

	public void closeNormal() {
		if (!startClosing(true))
			return;

		lobby.getContainer().close(closer);
	}

	public void closeNormal(boolean wait) throws TimeOutException {
		if (!startClosing(true))
			return;

		lobby.getContainer().close(closer, wait);
	}

	public void closeNormal(boolean wait, boolean force) throws TimeOutException {
		if (!startClosing(true))
			return;

		lobby.getContainer().close(closer, wait, force);
	}

	public void closeNormal(boolean wait, int timeout) throws TimeOutException {
		if (!startClosing(true))
			return;

		lobby.getContainer().close(closer, wait, timeout);
	}

	public void closeNormal(boolean wait, int timeout, boolean force) throws TimeOutException {
		if (!startClosing(true))
			return;

		lobby.getContainer().close(closer, wait, timeout, force);
	}

	private void closeObjects() {
		tmrCheckConnection.close();
		tmrTestConnection.close();

		if (input != null)
			try {
				input.close();
			} catch (IOException e) {
			}

		if (output != null)
			try {
				output.close();
			} catch (IOException e) {
			}

		if (socket != null)
			try {
				socket.close();
			} catch (IOException e) {
			}
	}

	public int closingMaxScore() {
		return 2;
	}

	public int closingScore() {
		return objectsClosed;
	}

	public void flush() {
		post(() -> {
			try {
				output.flush();
			} catch (IOException e) {
				handleIOException(e);
			}
		});
	}

	public int getAvatar() {
		return session.getUser().getAvatar();
	}

	public int getBirthDay() {
		return session.getUser().getBirthDay();
	}

	public int getBirthMonth() {
		return session.getUser().getBirthMonth();
	}

	public Date getBirthTimestamp() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(getBirthYear(), getBirthMonth(), getBirthDay());
		return calendar.getTime();
	}

	public int getBirthYear() {
		return session.getUser().getBirthYear();
	}

	public String getCompID() {
		return session.getUser().getCompID();
	}

	public String getCountry() {
		return session.getUser().getCountry();
	}

	public String getEmail() {
		return session.getUser().getEmail();
	}

	public String getFSID() {
		return session != null ? session.getFSID() : "";
	}

	public LobbyGame getGame() {
		return game;
	}

	public Gender getGender() {
		return session.getUser().getGender();
	}

	public String getGZID() {
		return session.getGZID();
	}

	public long getInactiveTime() {
		return System.currentTimeMillis() - lastReceivedTime;
	}

	protected ProcessQueue getInputQueue() {
		return inputQueue;
	}

	public InetAddress getIP() {
		return socket.getInetAddress();
	}

	public String getLang() {
		return session.getUser().getLang();
	}

	public Lobby getLobby() {
		return lobby;
	}

	public String getNick() {
		return session.getUser().getNick();
	}

	public int getOnlineStatus() {
		return ONLINESTATUS;
	}

	protected ProcessQueue getOutputQueue() {
		return getOutputQueue();
	}

	public int getPlayeds() {
		return session.getUser().getPlayeds();
	}

	public int getPlayeds(String variant) {
		return session.getUser().getPlayeds(variant);
	}

	public int getPort() {
		return socket.getPort();
	}

	public int getRating() {
		return session.getUser().getRating();
	}

	public int getRating2() {
		return session.getUser().getRating2();
	}

	public int getRating(String variant) {
		return session.getUser().getRating(variant);
	}

	public int getRating2(String variant) {
		return session.getUser().getRating2(variant);
	}

	public Timestamp getRegTimestamp() {
		return session.getUser().getRegistered();
	}

	public int getStars() {
		return session.getUser().getStars();
	}

	public synchronized int getState() {
		return state;
	}

	public int getWins() {
		return session.getUser().getWins();
	}

	public int getWins(String variant) {
		return session.getUser().getWins(variant);
	}

	protected void handleException(Throwable e) {
		if (e instanceof IOException)
			handleIOException((IOException) e);
		else if (e instanceof SQLException)
			handleSQLException((SQLException) e);
		else {
			lobby.getContainer().logToErr(toString(), e);
			closeAndSetReconnecting();
		}
	}

	protected void handleIOException(IOException e) {
		closeAndSetReconnecting();

		if (e instanceof EOFException)
			return;

		if (e instanceof SocketTimeoutException)
			return;

		if (e instanceof SocketException)
			return;

		lobby.getContainer().logToErr(toString(), e);
	}

	protected void handleSQLException(SQLException e) {
		lobby.getContainer().logToErr(toString(), e);
	}

	public void incrementDraws(String variant, int dr, int rating2) {
		UserStats stats = session.getUser().getStats(variant);
		stats.incrementDraws();
		stats.setRating(dr, rating2);
		lobby.getContainer().incrementDraws(variant, getGZID(), dr, rating2);
	}

	public void incrementLosses(String variant, boolean abandoned, int dr, int rating2) {
		UserStats stats = session.getUser().getStats(variant);
		stats.incrementLosses(abandoned);
		stats.setRating(dr, rating2);
		lobby.getContainer().incrementLosses(variant, getGZID(), abandoned, dr, rating2);
	}

	public void incrementWins(String variant, int dr, int rating2) {
		UserStats stats = session.getUser().getStats(variant);
		stats.incrementWins();
		stats.setRating(dr, rating2);
		lobby.getContainer().incrementWins(variant, getGZID(), dr, rating2);
	}

	@SuppressWarnings("unused")
	protected void init(Lobby lobby, boolean inGame, Socket socket) throws IOException {
		this.lobby = lobby;
		this.socket = socket;

		game = null;
		lastReceivedTime = -1;
		isMutedByFlood = false;

		spamCheck = new HashMap<>();

		List<SpamCheck> item = new ArrayList<>();
		item.add(new SpamCheck(RECEIVE_CHAT_MAX_MESSAGES, RECEIVE_CHAT_INTERVAL, (sc) -> {
			if (isMutedByFlood)
				lobby.getContainer().ban(Connection.this, CHAT_FLOOD_BAN_TIME, "chat flood");
			else {
				isMutedByFlood = true;
				lobby.getContainer().mute(Connection.this, CHAT_FLOOD_MUTE_TIME, "chat flood");
			}

			sc.reset();
			sc.block(SPAM_BLOCK_INTERVAL);

			lobby.getContainer().logToOut("[SPAM]",
					"Attempt to Chat Flood from connection " + Connection.this + " at lobby " + lobby.getName() + " in the game " + lobby.getContainer().getGameName() + " was blocked.");
		}));
		spamCheck.put("m", item);

		item = new ArrayList<>();
		item.add(new SpamCheck(DEFAULT_MAX_MESSAGES, DEFAULT_INTERVAL, (sc) -> {
			lobby.getContainer().ban(Connection.this, GENERIC_FLOOD_BAN_TIME, "generic command flood");

			sc.block(SPAM_BLOCK_INTERVAL);

			lobby.getContainer().logToOut("[SPAM]",
					"Attempt to Generic Flood from connection " + Connection.this + " at lobby " + lobby.getName() + " in the game " + lobby.getContainer().getGameName() + " was blocked.");
		}));
		spamCheck.put("*", item);

		group = new ThreadGroup(lobby.getGroup(), toString());

		inputQueue = new ProcessQueue(group, "Input Queue");
		inputQueue.addExceptionListener((e) -> handleException(e));

		outputQueue = new ProcessQueue(group, "Output Queue");
		outputQueue.addExceptionListener((e) -> handleException(e));
		outputQueue.addCloseListener(() -> onClose());

		tmrTestConnection = new Timer(outputQueue, TEST_CONNECTION_INTERVAL, true, (e) -> lobby.getContainer().logToErr(toString(), e));
		tmrTestConnection.addListener((timer, interval) -> {
			postMessage("_");
		});

		tmrCheckConnection = new Timer(outputQueue, CHECK_CONNECTION_INTERVAL, true, (e) -> lobby.getContainer().logToErr(toString(), e));
		tmrCheckConnection.addListener((timer, interval) -> closeAndSetReconnecting());

		state = OPENING;
		reconnecting = false;

		closer = new Closer(new Interruptable() {

			@Override
			public void close() {
				closeInternal();
			}

			@Override
			public void interrupt() {
				Connection.this.interrupt();
			}

		});

		sentCount = 0;
		receivedCount = 0;

		inputQueue.post(() -> {
			try {
				input = new BufferedInputStream(Connection.this.socket.getInputStream());
				output = new BufferedOutputStream(Connection.this.socket.getOutputStream());

				GZStruct response = new GZStruct();

				if (lobby.isClosing()) {
					postClose();
					return;
				}

				PTSTATUS = AUTH;

				synchronized (this) {
					state = AUTHENTICATING;
				}

				String incoming_data = readIncomingData();
				if (DEBUG_INPUT && DebugUtil.DEBUG_MODE)
					lobby.getContainer().logToOut(toString(), new String[] { "// Received from " + toString(), incoming_data.toString(), "" });

				if (incoming_data.equals("<policy-file-request/>")) {
					StringBuffer sb = new StringBuffer(90);
					sb.append("<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\"");
					sb.append(socket.getLocalPort());
					sb.append("\" /></cross-domain-policy>");
					socket_send_str(sb.toString());

					incoming_data = readIncomingData();
					if (DEBUG_INPUT && DebugUtil.DEBUG_MODE)
						lobby.getContainer().logToOut(toString(), new String[] { "// Received from " + toString(), incoming_data.toString(), "" });
				}

				if (!incoming_data.startsWith("sq")) {
					postClose();
					return;
				}

				ZF = incoming_data.substring(2);

				int off = GZUtils.randomRange(0, 20);
				localKey = GZUtils.hmx(Integer.toString(GZUtils.randomRange(0, 9999))).substring(off, off + 6);
				socket_send_str(localKey);

				incoming_data = readIncomingData();
				GZStruct request;
				try {
					request = GZUtils.unpack_packet(incoming_data);
					if (request == null) {
						postClose();
						return;
					}
				} catch (Throwable e) {
					postClose();
					return;
				}

				if (DEBUG_INPUT && DebugUtil.DEBUG_MODE)
					lobby.getContainer().logToOut(toString(), new String[] { "// Received from " + toString(), request.toString(), "" });

				String vd = request.getString("v");
				if (vd == null) {
					postClose();
					return;
				}
				
				remoteKey = request.getString("k");
				if (remoteKey == null) {
					postClose();
					return;
				}
				
				try {
					ONLINESTATUS = request.getInt("s");
				} catch (Throwable e) {
					postClose();
					return;
				}
				
				if (ONLINESTATUS < 1 || ONLINESTATUS > 5)
					ONLINESTATUS = 1;

				if (inGame) {
					String mode = request.getString("r");
					
					int gameid;
					try {
						gameid = request.getInt("ig");
					} catch (NumberFormatException e) {
						postClose();
						return;
					}
					
					game = lobby.getGameByID(gameid);
					if (game == null) {
						postClose();
						return;
					}
				}

				updateVD(vd);

				String checksum = remoteKey.substring(6);
				remoteKey = remoteKey.substring(0, 6);

				// if (DEBUG_INPUT)
				// lobby.getContainer().logToOut(toString(), new String[] {
				// "localKey=" + localKey, "remoteKey=" + remoteKey, "CRK=" +
				// CRK, "fsid=" + VD_DATA[2] });

				if (!GZUtils.hmx(remoteKey + localKey + CRK + VD_DATA[2] + remoteKey).substring(0, 8).toLowerCase().equals(checksum)) {
					response.setString("auth", "fail");
					send_mpack(response);
					postClose();
					return;
				}

				PTSTATUS = STD;
				remoteKey = GZUtils.hmx(remoteKey + ZF + localKey.substring(0, 3)).substring(10, 22).toLowerCase();
				zax = "";
				MODER_STATUS_NT = 0;

				String fsid = VD_DATA[2];
				Container servlet = lobby.getContainer();
				session = servlet.getSession(fsid);
				if (session == null) {
					response.setString("auth", "fail");
					send_mpack(response);
					postClose();
					return;
				}

				refresh();
				
				if (!isPremium() && (ONLINESTATUS == 2 || ONLINESTATUS == 4 || ONLINESTATUS == 5))
					ONLINESTATUS = 1;

				long banExpires = lobby.getContainer().getBanExpiresByGZIDOrCompidOrIP(lobby.getID(), this);
				if (banExpires != 0) {
					Calendar calendar = Calendar.getInstance();
					if (banExpires != -1)
						calendar.setTimeInMillis(banExpires);

					response.setString("auth", "fail");
					response.setString("alert", "You are banned from this lobby " + (banExpires == -1 ? "forever" : "until " + calendar.toInstant()) + ".");
					send_mpack(response);
					postClose();
					return;
				}

				if (!inGame) {
					lobby.addUser(this, (result) -> {
						inputQueue.post(() -> {
							if (!result) {
								postClose();
								return;
							}

							try {
								lobby.getContainer().executeTransaction((connection) -> {
									try (ResultSet rs = connection.insert(DBV5Consts.TABLE_ACCESS_LOG, true, new Object[] { null, getGZID(), new Timestamp(System.currentTimeMillis()), getCompID(),
											getIP().getHostAddress(), lobby.getContainer().getGameName(), lobby.getID() })) {
										if (!rs.next())
											return null;

										int lastInsertID = rs.getInt(1);

										connection.update(DBV5Consts.TABLE_USERS, new String[] { "gzid" }, new Object[] { getGZID() }, new String[] { "compid", "last_access" },
												new Object[] { getCompID(), lastInsertID });
									}
									
									return null;
								});

								completeHandshake();
							} catch (SQLException e) {
								handleException(e);
							} catch (InterruptedException e) {
								postClose();
							}
						});
					});
				} else
					game.addUser(this, (result) -> {
						inputQueue.post(() -> {
							if (!result) {
								postClose();
								return;
							}

							completeHandshake();
						});
					});
			} catch (Throwable e) {
				handleException(e);
			}
		}, (e) -> lobby.getContainer().logToErr(toString(), e));
	}

	private void completeHandshake() {
		tmrTestConnection.play();
		tmrCheckConnection.play();
		lastReceivedTime = System.currentTimeMillis();

		inputQueue.setName("Connection input queue [" + getGZID() + "]");
		outputQueue.setName("Connection output queue [" + getGZID() + "]");

		PTSTATUS = CONNECTED;

		synchronized (this) {
			state = OPEN;
		}
		
		inputQueue.post(() -> {
			if (getState() != OPEN) {
				postClose();
				return;
			}

			try {
				String incoming_data = readIncomingData();

				tmrCheckConnection.reset();

				if (incoming_data.equals("_"))
					return;

				receivedCount++;

				if (DEBUG_INPUT && DebugUtil.DEBUG_MODE)
					lobby.getContainer().logToOut(toString(), new String[] { "// Received from " + toString(), incoming_data, "" });

				int p = incoming_data.indexOf("_");
				if (p != -1) {
					String checksum = incoming_data.substring(0, p);
					String s = checksum.substring(4);

					int time;
					try {
						time = Integer.parseInt(s);
					} catch (NumberFormatException e) {
						postClose();
						return;
					}

					checksum = checksum.substring(0, 4);
					incoming_data = incoming_data.substring(p + 1);

					if (!GZUtils.hmx(remoteKey + localKey + receivedCount + incoming_data + time).substring(27, 31).toLowerCase().equals(checksum)) {
						postClose();
						return;
					}
				}

				GZStruct request;
				try {
					request = GZUtils.unpack_packet(incoming_data);
					if (request == null) {
						postClose();
						return;
					}
				} catch (Throwable e) {
					postClose();
					return;
				}
				
				String opcode = request.getString("c");
				if (opcode == null) {
					postClose();
					return;
				}

				List<SpamCheck> list = spamCheck.get(opcode);
				if (list == null)
					list = spamCheck.get("*");

				for (SpamCheck sc : list) {
					if (sc.isBlocked())
						return;

					if (!sc.check())
						return;
				}

				if (game != null)
					game.parseData(this, opcode, request);
				else
					lobby.parseData(this, opcode, request);

				lastReceivedTime = System.currentTimeMillis();
			} catch (Throwable e) {
				handleException(e);
				postClose();
				return;
			}
		}, 0, (e) -> lobby.getContainer().logToErr(toString(), e));
	}

	@Override
	public void interrupt() {
		closeObjects();

		group.interrupt();

		lobby.getContainer().logToErr(toString(), "WARNING: All threads were interrupted.");
	}

	public boolean isAdmin(int lobby) {
		if (session == null)
			return false;

		User user = session.getUser();
		return user.isSystem() || this.lobby.getContainer().isAdmin(lobby, getGZID());
	}

	public boolean isBlocked() {
		return session != null ? lobby.getContainer().isMuted(lobby.getID(), this) : false;
	}

	public synchronized boolean isClosed() {
		return state == CLOSING && closer.isClosed();
	}

	public synchronized boolean isClosing() {
		return state == CLOSING && closer.isClosing();
	}

	public synchronized boolean isOpen() {
		return state == OPEN;
	}

	public boolean isPremium() {
		return session != null ? session.getUser().isPremium() : false;
	}

	public boolean isSystem() {
		return session != null ? session.getUser().isSystem() : false;
	}

	private void onClose() {
		closeObjects();

		closer.stopClosing();
	}

	public void post(NonReturnableProcessWithoutArg process) {
		outputQueue.post(process, (e) -> lobby.getContainer().logToErr(Connection.this.toString(), e));
	}

	public void postAndWait(NonReturnableProcessWithoutArg process) throws InterruptedException {
		try {
			outputQueue.postAndWait(process);
		} catch (RuntimeException e) {
			lobby.getContainer().logToErr(toString(), e);
		}
	}

	public void postBlock(GZStruct response) {
		postBlock(response, true);
	}

	public void postBlock(GZStruct response, boolean flush) {
		post(() -> send_mpack(response, flush));
	}

	public void postClose() {
		post(() -> closeNormal());
	}

	public void postCloseAndSetReconnecting() {
		post(() -> closeAndSetReconnecting());
	}

	public void postMessage(String message) {
		postMessage(message, true);
	}

	public void postMessage(String message, boolean flush) {
		post(() -> socket_send_str(message, flush));
	}

	private String readIncomingData() throws IOException {
		if (input == null)
			throw new IOException("The input is null.");

		String result = "";
		while (true) {
			int i = input.read();
			if (i == -1)
				throw new EOFException();

			if (i == 0)
				return result;

			result += (char) i;
		}
	}

	public void refresh() {
		User user = session.getUser();
		user.refresh();
	}

	public void send(NonReturnableProcessWithoutArg process) {
		try {
			outputQueue.send(process);
		} catch (InterruptedException e) {
			close();
		} catch (RuntimeException e) {
			lobby.getContainer().logToErr(toString(), e);
		}
	}

	public <T> T send(ReturnableProcess<T> process) {
		try {
			return outputQueue.send(process);
		} catch (InterruptedException e) {
			close();
		} catch (RuntimeException e) {
			lobby.getContainer().logToErr(toString(), e);
		}

		return null;
	}

	public void send_mpack(GZStruct arr_data) {
		send_mpack(arr_data, true);
	}

	@SuppressWarnings("unused")
	public void send_mpack(GZStruct arr_data, boolean flush) {
		if (DEBUG_OUTPUT && DebugUtil.DEBUG_MODE)
			lobby.getContainer().logToOut(toString(), new String[] { "// Sent to " + toString(), arr_data.toString(), "" });

		long currentTime = System.currentTimeMillis();
		long deltaTime;
		if (lastSentTime <= 0)
			deltaTime = 0;
		else
			deltaTime = currentTime - lastSentTime;

		lastSentTime = currentTime;
		++sentCount;
		String packed = GZUtils.pack_packet(arr_data);
		String prefix = GZUtils.hmx(localKey + remoteKey + sentCount + packed).substring(17, 21);

		String message = prefix + packed;

		socket_send_str(message, flush);
	}

	public void setOnlineStatus(int value) {
		ONLINESTATUS = value;
	}

	private void socket_send_str(String str) {
		socket_send_str(str, true);
	}

	private void socket_send_str(String str, boolean flush) {
		byte buf[] = str.getBytes();
		try {
			output.write(buf);
			output.write(0);

			if (flush)
				output.flush();
		} catch (IOException e) {
			handleIOException(e);
		}
	}

	private synchronized boolean startClosing(boolean normalExit) {
		if (state == CLOSING)
			return false;

		state = CLOSING;
		reconnecting = !normalExit;

		return true;
	}

	@Override
	public String toString() {
		return "connection" + (session != null ? " " + getNick() + " (" + getGZID() + ")" : "") + " [" + getIP().getHostAddress() + ":" + getPort() + "] at the "
				+ (game != null ? game.toString() : lobby != null ? lobby.toString() : "???");
	}

	private void updateVD(String vd) {
		if (vd != null) {
			VD_DATA_STR = vd;
			VD_DATA = GZUtils.lim_split(vd, ":", 27);
			if (VD_DATA.length > 2)
				CRK = GZUtils.hmx(VD_DATA[2]).substring(0, 20);
		}
	}

	public boolean isAdmin() {
		if (session == null)
			return false;

		User user = session.getUser();
		return user.isSystem() || this.lobby.getContainer().isAdmin(getGZID());
	}

	public Inventory getActiveInventory(String type) {
		return session.getUser().getActiveInventory(type);
	}

}
