package gz.server.net;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.mysql.jdbc.AbandonedConnectionCleanupThread;

import common.config.Config;
import common.config.ConfigEntry;
import common.config.MapConfigEntry;
import common.config.SimpleConfigEntry;
import common.db.SQLCommand;
import common.db.SQLConnection;
import common.db.SQLConnectionPool;
import common.db.SQLExpression;
import common.db.SQLFormater;
import common.db.Transaction;
import common.io.Log;
import common.process.Closer;
import common.process.Interruptable;
import common.process.NonReturnableProcessWithoutArg;
import common.process.ProcessQueue;
import common.process.ReturnableProcess;
import common.process.TimeOutException;
import common.process.timer.Timer;
import common.util.DateTimeUtil;
import common.util.DebugUtil;
import common.util.DigestUtil;
import common.util.MailUtil;
import common.util.PatternUtil;
import common.util.RandomUtil;
import common.util.Tree;
import common.util.URLUtil;
import gz.common.io.DirectoryWatcher;
import gz.common.io.DirectoryWatcherListener;
import gz.common.logic.Game.StopReason;
import gz.net.Gender;
import gz.shared.DBV5Consts;
import gz.util.GZStruct;
import gz.util.GZUtils;

public abstract class Container extends HttpServlet implements Interruptable {

	private static final int DEFAULT_REGISTERED_ACCOUNT_CHECK_PERIOD_SEC = 24 * 60 * 60; // 1
																							// day
	private static final int MAX_ACCOUNTS_REGISTERS_PER_DAY = 3;

	public class Admin {

		private int lobby;
		private String gzid;
		private int level;
		private String addedBy;
		private Timestamp addedWhen;
		private boolean active;

		private Admin(String gzid, ResultSet rs) throws SQLException {
			this.gzid = gzid;

			lobby = rs.getInt("room");
			level = rs.getInt("level");
			addedBy = rs.getString("added_by");
			addedWhen = rs.getTimestamp("added_when");
			active = rs.getBoolean("active");
		}

		public String getAddedBy() {
			return addedBy;
		}

		public Timestamp getAddedWhen() {
			return addedWhen;
		}

		public String getGzid() {
			return gzid;
		}

		public int getLevel() {
			return level;
		}

		public int getLobby() {
			return lobby;
		}

		public boolean isActive() {
			return active;
		}

	}

	public static enum BanRestriction {
		CHAT, ACCESS
	}

	public static enum BanType {
		GZID, COMPID, IP
	}

	protected class Captcha {
		private String vn;
		private String vs;
		private String vt;
		private Timestamp created;

		private Captcha(ResultSet rs) throws SQLException {
			vn = rs.getString("vn");
			vs = rs.getString("vs");
			vt = rs.getString("vt");
			created = rs.getTimestamp("created");
		}

		private Captcha(String vn, String vs, String vt, Timestamp created) {
			this.vn = vn;
			this.vs = vs;
			this.vt = vt;
			this.created = created;
		}

		public Timestamp getCreated() {
			return created;
		}

		public String getVN() {
			return vn;
		}

		public String getVS() {
			return vs;
		}

		public String getVT() {
			return vt;
		}

		public boolean isExpired() {
			return created.getTime() + DEFAULT_EXPIRES2 <= System.currentTimeMillis();
		}
	}

	public class PremiumData {
		private String gzid;
		private Timestamp startedWhen;
		private long duration;

		private PremiumData(String gzid, ResultSet rs) throws SQLException {
			this.gzid = gzid;

			readFromResultSet(rs);
		}

		public long getDuration() {
			return duration;
		}

		public String getGzid() {
			return gzid;
		}

		public Timestamp getStartedWhen() {
			return startedWhen;
		}

		public boolean isExpired() {
			return duration != -1 ? startedWhen.getTime() + duration <= System.currentTimeMillis() : false;
		}

		private void readFromResultSet(ResultSet rs) throws SQLException {
			startedWhen = rs.getTimestamp("started_when");
			duration = rs.getLong("duration");
		}

		public void refresh() {
			try {
				executeTransaction((connection) -> {
					try (ResultSet rs = connection.executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_PREMIUMS, new String[] { "gzid" }, new Object[] { gzid }))) {
						if (!rs.next())
							logToErr("User from gzid " + gzid + " was deleted from the table " + DBV5Consts.TABLE_PREMIUMS + ".");
						else
							readFromResultSet(rs);
					}

					return null;
				});
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}
		}
	}

	public class Session {
		private User user;
		private String fsid;
		private String gzid;
		private Timestamp created;
		private int expires;
		private int expires2;
		private IPSession ipSession;

		private Session(User user) {
			this.user = user;

			gzid = user.getGZID();
			long time = System.currentTimeMillis();
			created = new Timestamp(time);
			expires = DEFAULT_EXPIRES;
			expires2 = DEFAULT_EXPIRES2;
			fsid = generateFSID(gzid, time);
		}

		private Session(User user, ResultSet rs) throws SQLException {
			readFromCookiesTable(user, rs);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;

			if (obj == null)
				return false;

			if (!(obj instanceof Session))
				return false;

			Session other = (Session) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;

			if (fsid == null) {
				if (other.fsid != null)
					return false;
			} else if (!fsid.equals(other.fsid))
				return false;

			return true;
		}

		public Timestamp getCreated() {
			return created;
		}

		public int getExpires() {
			return expires;
		}

		public int getExpires2() {
			return expires2;
		}

		public String getFSID() {
			return fsid;
		}

		public String getGZID() {
			return gzid;
		}

		public IPSession getIPSession() {
			return ipSession;
		}

		private Container getOuterType() {
			return Container.this;
		}

		public UserStats getStats(String variant) {
			return user.getStats(variant);
		}

		public UserStats getStats(Variant variant) {
			return user.getStats(variant);
		}

		public User getUser() {
			return user;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + (fsid == null ? 0 : fsid.hashCode());
			return result;
		}

		public boolean isExpired() {
			return expires == -1 ? false : System.currentTimeMillis() >= created.getTime() + expires;
		}

		public boolean isOnline() {
			return expires2 == -1 ? true : System.currentTimeMillis() < created.getTime() + expires2;
		}

		public void logout() {
			sessions.remove(fsid);

			try {
				executeTransaction((connection) -> {
					connection.delete(DBV5Consts.TABLE_SESSIONS, new String[] { "gzid", "fsid" }, new Object[] { gzid, fsid });

					return null;
				});
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}
		}

		private void readFromCookiesTable(User user, ResultSet rs) throws SQLException {
			this.user = user;

			fsid = rs.getString("fsid");
			gzid = rs.getString("gzid");
			created = rs.getTimestamp("created");
			expires = rs.getInt("expires");
		}

		@Override
		public String toString() {
			return "session " + fsid + " from user " + user + " created/updated in " + created;
		}
	}

	// Máximo de
	private static final int IP_SPAM_CHECK_MAX_MESSAGES = 10; // 10 requisições
	// a cada
	private static final int IP_SPAM_CHECK_MAX_INTERVAL = 2 * 1000; // 2
																	// segundos
	// Em caso de SPAM, bloqueie o ip por
	private static final int SPAM_BLOCK_INTERVAL = 10 * 60 * 1000; // 10 minutos

	public class IPSession {
		private String ip;
		private SpamCheck spamCheck;

		protected IPSession(String ip) {
			this.ip = ip;

			spamCheck = new SpamCheck(IP_SPAM_CHECK_MAX_MESSAGES, IP_SPAM_CHECK_MAX_INTERVAL, (sc) -> {
				banByIP(ip, SPAM_BLOCK_INTERVAL, "ip spam flood");
				spamCheck.block(SPAM_BLOCK_INTERVAL);

				logToOut("[SPAM]", "Attempt to SPAM Request from ip " + ip + " was blocked.");
			});
		}

		public String getIP() {
			return ip;
		}

		protected boolean checkSpam() {
			return spamCheck.check();
		}

		public boolean isBlocked() {
			return spamCheck.isBlocked();
		}
	}

	public class User {
		private String gzid;
		private String email;
		private String password;
		private String compid;
		private Timestamp registered;
		private boolean verified;
		private Timestamp lastLogin;
		private Timestamp lastAccess;
		private boolean banned;
		private Timestamp bannedWhen;
		private int banDuration;
		private String banReason;
		private boolean system;
		private boolean active;
		private int avatar;
		private String lang;
		private String country;
		private int bday;
		private int bmonth;
		private int byear;
		private boolean allowp;
		private boolean allowe;
		private boolean allowc;
		private Gender gender;
		private int qst;
		private String ans;
		private String nick;
		private int stars;
		private Vector<UserStats> stats;
		private Hashtable<Variant, Integer> statsMap;
		private PremiumData premiumData;
		private String ip;
		private Group group;
		private Hashtable<String, List<Inventory>> inventories;

		private User(ResultSet rs) throws SQLException {
			stats = new Vector<>();
			statsMap = new Hashtable<>();
			inventories = new Hashtable<>();

			readFromResultSet(rs);
			premiumData = null;
			group = null;
		}

		public int getAbandoneds() {
			int result = 0;
			for (Variant variant : variants) {
				UserStats stats = getStats(variant);
				result += stats.getAbandoneds();
			}

			return result;
		}

		public int getAbandoneds(String variant) {
			return getStats(variant).getAbandoneds();
		}

		public Set<String> inventoryTypes() {
			return Collections.unmodifiableSet(inventories.keySet());
		}

		public List<Inventory> inventories(String type) {
			List<Inventory> list = inventories.get(type);
			if (list == null)
				return null;

			return Collections.unmodifiableList(list);
		}

		public Inventory getActiveInventory(String type) {
			List<Inventory> list = inventories.get(type);
			if (list == null)
				return null;

			for (Inventory inventory : list)
				if (inventory.isActive())
					return inventory;

			return null;
		}

		public String getAnswer() {
			return ans;
		}

		public int getAvatar() {
			return avatar;
		}

		public int getBanDuration() {
			return banDuration;
		}

		public Timestamp getBannedWhen() {
			return bannedWhen;
		}

		public String getBanReason() {
			return banReason;
		}

		public int getBirthDay() {
			return bday;
		}

		public int getBirthMonth() {
			return bmonth;
		}

		public int getBirthYear() {
			return byear;
		}

		public String getCompID() {
			return compid;
		}

		public String getCountry() {
			return country;
		}

		public int getDraws() {
			int result = 0;
			for (Variant variant : variants) {
				UserStats stats = getStats(variant);
				result += stats.getDraws();
			}

			return result;
		}

		public int getDraws(String variant) {
			return getStats(variant).getDraws();
		}

		public String getEmail() {
			return email;
		}

		public Gender getGender() {
			return gender;
		}

		public String getGZID() {
			return gzid;
		}

		public String getIP() {
			return ip;
		}

		public String getLang() {
			return lang;
		}

		public Timestamp getLastAccess() {
			return lastAccess;
		}

		public Timestamp getLastLogin() {
			return lastLogin;
		}

		public int getLosses() {
			int result = 0;
			for (Variant variant : variants) {
				UserStats stats = getStats(variant);
				result += stats.getLosses();
			}

			return result;
		}

		public int getLosses(String variant) {
			return getStats(variant).getLosses();
		}

		public String getNick() {
			return nick;
		}

		public String getPassword() {
			return password;
		}

		public int getPlayeds() {
			int result = 0;
			for (Variant variant : variants) {
				UserStats stats = getStats(variant);
				result += stats.getPlayeds();
			}

			return result;
		}

		public int getPlayeds(String variant) {
			return getStats(variant).getPlayeds();
		}

		public PremiumData getPremiumData() {
			return premiumData;
		}

		public boolean isPremium() {
			return getRating() / 1000 >= 100 || premiumData != null && !premiumData.isExpired();
		}

		public int getQuestion() {
			return qst;
		}

		public int getRating() {
			int result = 0;
			for (Variant variant : variants) {
				UserStats stats = getStats(variant);
				result += stats.getRating();
			}

			return result;
		}

		public int getRating(String variant) {
			return getStats(variant).getRating();
		}

		public int getRating2(String variant) {
			return getStats(variant).getRating2();
		}

		public Timestamp getRegistered() {
			return registered;
		}

		public int getStars() {
			return stars;
		}

		public UserStats getStats(String variant) {
			return getStats(getVariant(variant));
		}

		public UserStats getStats(Variant variant) {
			Integer index = statsMap.get(variant);
			if (index == null)
				return null;

			return stats.get(index);
		}

		public int getWins() {
			int result = 0;
			for (Variant variant : variants) {
				UserStats stats = getStats(variant);
				result += stats.getWins();
			}

			return result;
		}

		public int getWins(String variant) {
			return getStats(variant).getWins();
		}

		public boolean isActive() {
			return active;
		}

		public boolean isBanned() {
			return banned;
		}

		public boolean isShowingCountry() {
			return allowc;
		}

		public boolean isShowingGender() {
			return allowp;
		}

		public boolean isShowingGZID() {
			return allowe;
		}

		public boolean isSystem() {
			return system;
		}

		public boolean isVerified() {
			return verified;
		}

		private void readFromResultSet(ResultSet rs) throws SQLException {
			gzid = rs.getString("gzid");
			email = rs.getString("email");
			password = URLUtil.urlDecode(rs.getString("password"));
			compid = rs.getString("compid");
			registered = rs.getTimestamp("registered");
			verified = rs.getBoolean("verified");
			lastLogin = rs.getTimestamp(DBV5Consts.TABLE_LOGIN_LOG + ".when");
			lastAccess = rs.getTimestamp(DBV5Consts.TABLE_ACCESS_LOG + ".when");

			if (lastLogin != null) {
				if (lastAccess != null && lastLogin.compareTo(lastAccess) < 0)
					ip = rs.getString(DBV5Consts.TABLE_ACCESS_LOG + ".ip");
				else
					ip = rs.getString(DBV5Consts.TABLE_LOGIN_LOG + ".ip");
			} else if (lastAccess != null)
				ip = rs.getString(DBV5Consts.TABLE_ACCESS_LOG + ".ip");
			else
				ip = null;

			banned = rs.getBoolean("banned");
			bannedWhen = rs.getTimestamp("banned_when");
			banDuration = rs.getInt("ban_duration");
			banReason = URLUtil.urlDecode(rs.getString("ban_reason"));
			system = rs.getBoolean("system");
			active = rs.getBoolean("active");
			avatar = rs.getInt("avatar");
			lang = rs.getString("lang");
			country = rs.getString("country");
			bday = rs.getInt("bday");
			bmonth = rs.getInt("bmonth");
			byear = rs.getInt("byear");
			allowp = rs.getBoolean("allowp");
			allowe = rs.getBoolean("allowe");
			allowc = rs.getBoolean("allowc");

			int gender = rs.getInt("gender");
			if (gender == 0)
				this.gender = Gender.MALE;
			else
				this.gender = Gender.FEMALE;

			qst = rs.getInt("qst");
			ans = URLUtil.urlDecode(rs.getString("ans"));
			nick = URLUtil.urlDecode(rs.getString("nick"));
			stars = rs.getInt("stars");

			synchronized (stats) {
				for (UserStats stat : stats)
					stat.refresh();
			}

			if (premiumData != null)
				premiumData.refresh();
		}

		public void refresh() {
			try {
				executeTransaction((connection) -> {
					try (ResultSet rs = connection.executeQuery(makeUsersJoinByGZID(gzid))) {
						if (!rs.next())
							logToErr("User from gzid " + gzid + " was deleted from the table " + DBV5Consts.TABLE_USERS + ".");
						else
							readFromResultSet(rs);
					}

					return null;
				});
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}
		}

		@Override
		public String toString() {
			return nick + "(" + gzid + ")";
		}

		public void updatePremiumData(PremiumData premiumData) {
			this.premiumData = premiumData;
		}

		protected void updateStats(String variant, UserStats stats) {
			updateStats(getVariant(variant), stats);
		}

		protected void updateStats(Variant variant, UserStats stats) {
			int index = this.stats.indexOf(stats);
			if (index == -1) {
				this.stats.add(stats);
				statsMap.put(variant, this.stats.size() - 1);
			} else
				this.stats.set(index, stats);
		}

		public void updateStatsFromStatsTable(String variant, ResultSet rs) throws SQLException {
			updateStatsFromStatsTable(getVariant(variant), rs);
		}

		public void updateStatsFromStatsTable(Variant variant, ResultSet rs) throws SQLException {
			updateStats(variant, new UserStats(variant, gzid, rs));
		}

		public Group getGroup() {
			return group;
		}

		public int getRating2() {
			int result = 0;
			for (Variant variant : variants) {
				UserStats stats = getStats(variant);
				result += stats.getRating2();
			}

			return result;
		}
	}

	public class UserStats {
		private Variant variant;
		private String gzid;
		private int playeds;
		private int abandoneds;
		private int wins;
		private int losses;
		private int draws;
		private int rating;
		private int rating2;
		private int streak;

		private UserStats(String variant, String gzid) {
			this(Container.this.getVariant(variant), gzid);
		}

		private UserStats(String variant, String gzid, ResultSet rs) throws SQLException {
			this(Container.this.getVariant(variant), gzid, rs);
		}

		private UserStats(Variant variant, String gzid) {
			this.variant = variant;
			this.gzid = gzid;

			playeds = 0;
			abandoneds = 0;
			wins = 0;
			losses = 0;
			draws = 0;
			rating = 0;
			rating2 = 0;
			streak = 0;
		}

		private UserStats(Variant variant, String gzid, ResultSet rs) throws SQLException {
			this.variant = variant;
			this.gzid = gzid;

			readFromResultSet(rs);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;

			if (obj == null)
				return false;

			if (!(obj instanceof UserStats))
				return false;

			UserStats other = (UserStats) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;

			if (gzid == null) {
				if (other.gzid != null)
					return false;

			} else if (!gzid.equals(other.gzid))
				return false;

			if (variant == null) {
				if (other.variant != null)
					return false;

			} else if (!variant.equals(other.variant))
				return false;

			return true;
		}

		public int getAbandoneds() {
			return abandoneds;
		}

		public int getDraws() {
			return draws;
		}

		public String getGZID() {
			return gzid;
		}

		public int getLosses() {
			return losses;
		}

		private Container getOuterType() {
			return Container.this;
		}

		public int getPlayeds() {
			return playeds;
		}

		public int getRating() {
			return rating;
		}

		public int getRating2() {
			return rating2;
		}

		public int getStreak() {
			return streak;
		}

		public Variant getVariant() {
			return variant;
		}

		public int getWins() {
			return wins;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + (gzid == null ? 0 : gzid.hashCode());
			result = prime * result + (variant == null ? 0 : variant.hashCode());
			return result;
		}

		public void incrementDraws() {
			playeds++;
			draws++;
		}

		public void incrementLosses(boolean abandoned) {
			playeds++;
			losses++;
			if (abandoned)
				abandoneds++;
		}

		public void incrementWins() {
			playeds++;
			wins++;
		}

		private void readFromResultSet(ResultSet rs) throws SQLException {
			playeds = rs.getInt("playeds");
			abandoneds = rs.getInt("abandoneds");
			wins = rs.getInt("wins");
			losses = rs.getInt("losses");
			draws = rs.getInt("draws");
			rating = rs.getInt("rating");
			rating2 = rs.getInt("rating2");
			streak = rs.getInt("streak");
		}

		public void refresh() {
			try {
				executeTransaction((connection) -> {
					try (ResultSet rs = connection
							.executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_STATS, new String[] { "game", "variant", "gzid" }, new Object[] { getGameName(), variant.getName(), gzid }))) {
						if (rs.next())
							readFromResultSet(rs);
					}

					return null;
				});
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}
		}

		public void setRating(int dr, int rating2) {
			this.rating += dr;
			this.rating2 = rating2;
		}

		@Override
		public String toString() {
			return "UserStats [variant=" + variant + ", gzid=" + gzid + ", playeds=" + playeds + ", abandoneds=" + abandoneds + ", wins=" + wins + ", losses=" + losses + ", draws=" + draws
					+ ", rating=" + rating + ", rating2=" + rating2 + ", streak=" + streak + "]";
		}
	}

	public class Group {
		private String id;
		private String password;
		private String name;
		private Timestamp created;
		private String owner;
		private int memberCount;
		private boolean active;

		protected Group(ResultSet rs) throws SQLException {
			id = rs.getString("id");
			password = rs.getString("password");
			name = rs.getString("name");
			created = rs.getTimestamp("created");
			owner = rs.getString("owner");
			memberCount = rs.getInt("member_count");
			active = rs.getBoolean("active");
		}

		protected Group(String id, String password, String name, Timestamp created, String owner) {
			this.id = id;
			this.password = password;
			this.name = name;
			this.created = created;
			this.owner = owner;

			memberCount = 1;
			active = true;
		}

		public String getID() {
			return id;
		}

		public String getName() {
			return name;
		}

		public Timestamp getCreated() {
			return created;
		}

		public String getOwner() {
			return owner;
		}

		public boolean isActive() {
			return active;
		}

		public int getMemberCount() {
			return memberCount;
		}

		public String getPassword() {
			return password;
		}
	}

	public class Variant {
		private String name;
		private String title;

		protected Variant(String name, String title) {
			this.name = name;
			this.title = title;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;

			if (obj == null)
				return false;

			if (!(obj instanceof Variant))
				return false;

			Variant other = (Variant) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;

			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;

			return true;
		}

		public String getName() {
			return name;
		}

		private Container getOuterType() {
			return Container.this;
		}

		public String getTitle() {
			return title;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + (name == null ? 0 : name.hashCode());
			return result;
		}

		@Override
		public String toString() {
			return "Variant [name=" + name + ", title=" + title + "]";
		}
	}

	public class MailService {

		private String id;
		private String host;
		private int port;
		private String sender;
		private String password;
		private boolean useSSL;
		private boolean useAuth;

		protected MailService(ResultSet rs) throws SQLException {
			id = rs.getString("id");
			host = rs.getString("host");
			port = rs.getInt("port");
			sender = rs.getString("sender");
			password = rs.getString("password");
			useSSL = rs.getBoolean("use_ssl");
			useAuth = rs.getBoolean("use_auth");
		}

		public String getID() {
			return id;
		}

		public String getHost() {
			return host;
		}

		public int getPort() {
			return port;
		}

		public String getSender() {
			return sender;
		}

		public String getPassword() {
			return password;
		}

		public boolean useSSL() {
			return useSSL;
		}

		public boolean useAuth() {
			return useAuth;
		}

	}

	public class Inventory {

		private String gzid;
		private String item;
		private String type;
		private Timestamp addedWhen;
		private String addedBy;
		private boolean active;

		public Inventory(ResultSet rs) throws SQLException {
			gzid = rs.getString("gzid");
			item = rs.getString("item");
			type = rs.getString("type");
			addedWhen = rs.getTimestamp("added_when");
			addedBy = rs.getString("added_by");
			active = rs.getBoolean("active");
		}

		public String getGzid() {
			return gzid;
		}

		public String getItem() {
			return item;
		}

		public String getType() {
			return type;
		}

		public Timestamp getAddedWhen() {
			return addedWhen;
		}

		public String getAddedBy() {
			return addedBy;
		}

		public boolean isActive() {
			return active;
		}
	}

	private static final int MIN_RATING = 0;

	private static final int MAX_RATING = Integer.MAX_VALUE;

	private static final int INITIAL_RATING = 0;

	/**
	 * 
	 */
	private static final long serialVersionUID = 5459376611021587785L;

	private static final boolean DEBUG_SERVICE = true;

	private static final String DEFAULT_HOME_DIR = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1 ? "C:\\gzv5\\" : "/home/gzv5/";

	private static final int DEFAULT_EXPIRES = 24 * 60 * 60 * 1000; // 1 dia

	private static final int DEFAULT_EXPIRES2 = 5 * 60 * 1000; // 5 minutos

	public static final int CLEANUP_INTERVAL = 60 * 60 * 1000; // 1 hora

	private static final char[] VT_CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'K', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
			'V', 'W', 'X', 'Y', 'Z' };

	private static final int CAPTCHA_WIDTH = 40;

	private static final int CAPTCHA_HEIGHT = 20;

	private static <T extends Enum<T>> T asEnum(Class<T> enumType, String name) {
		return Enum.valueOf(enumType, name.trim());
	}

	private static <T extends Enum<T>> Set<T> asSet(Class<T> enumType, String value) {
		Set<T> result = EnumSet.noneOf(enumType);
		value = value.trim();
		if (value.equals(""))
			return result;

		String[] values = value.split(",");
		for (String name : values) {
			T e = asEnum(enumType, name);
			result.add(e);
		}

		return result;
	}

	private static String generateFSID(String gzsid, long time) {
		return DigestUtil.md5(gzsid + DigestUtil.md5(Long.toString(time ^ RandomUtil.randomRange(Short.MAX_VALUE, 4 * Short.MAX_VALUE - 1))));
	}

	private static String printColor(int rgb) {
		String result = Long.toString(rgb & 0xffffffffL, 16);
		for (int i = 0; i < 6 - result.length(); i++)
			result = "0" + result;

		return result;
	}

	private static final int MAX_LOGIN_ATTEMPT_FAILS = 5;
	private static final int BLOCK_TIME_WHEN_MAX_LOGIN_ATTEMPS_REACHED = 10 * 60 * 1000; // 10
																							// minutos

	protected class LoginIPPair {
		private String email;
		private String ip;

		protected LoginIPPair(String email, String ip) {
			this.email = email;
			this.ip = ip;
		}

		public String getEmail() {
			return email;
		}

		public String getIP() {
			return ip;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((email == null) ? 0 : email.hashCode());
			result = prime * result + ((ip == null) ? 0 : ip.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof LoginIPPair)) {
				return false;
			}
			LoginIPPair other = (LoginIPPair) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (email == null) {
				if (other.email != null) {
					return false;
				}
			} else if (!email.equals(other.email)) {
				return false;
			}
			if (ip == null) {
				if (other.ip != null) {
					return false;
				}
			} else if (!ip.equals(other.ip)) {
				return false;
			}
			return true;
		}

		private Container getOuterType() {
			return Container.this;
		}
	}

	protected class LoginAttemp {
		private LoginIPPair lip;

		private int tries;
		private boolean blocked;
		private long blockedWhen;

		protected LoginAttemp(String email, String ip) {
			this(new LoginIPPair(email, ip));
		}

		protected LoginAttemp(LoginIPPair lip) {
			this.lip = lip;

			tries = 0;
			blocked = false;
			blockedWhen = -1;
		}

		public String getEmail() {
			return lip.getEmail();
		}

		public String getIP() {
			return lip.getIP();
		}

		public int getTries() {
			return tries;
		}

		private void ensureIsBlocked() {
			if (blocked) {
				if (blockedWhen + BLOCK_TIME_WHEN_MAX_LOGIN_ATTEMPS_REACHED <= System.currentTimeMillis()) {
					blocked = false;
					tries = 0;
				}
			}
		}

		public boolean isBlocked() {
			ensureIsBlocked();
			return blocked;
		}

		public void increment() {
			tries++;
			if (tries >= MAX_LOGIN_ATTEMPT_FAILS) {
				blocked = true;
				blockedWhen = System.currentTimeMillis();
			}
		}

		public void reset() {
			tries = 0;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + ((lip == null) ? 0 : lip.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof LoginAttemp)) {
				return false;
			}
			LoginAttemp other = (LoginAttemp) obj;
			if (!getOuterType().equals(other.getOuterType())) {
				return false;
			}
			if (lip == null) {
				if (other.lip != null) {
					return false;
				}
			} else if (!lip.equals(other.lip)) {
				return false;
			}
			return true;
		}

		private Container getOuterType() {
			return Container.this;
		}

		public long getBlockedWhen() {
			ensureIsBlocked();
			return blocked ? blockedWhen : -1;
		}
	}

	private Timer tmrCleanupDatabase;

	private Hashtable<String, Session> sessions;
	private Hashtable<String, IPSession> ips;

	private Hashtable<LoginIPPair, LoginAttemp> loginAttempts;

	private Closer closer;
	private int objectsToClose;
	private int objectsClosed;

	private Config config;
	private File homeDir;
	private String logDir;
	private HashMap<String, Class<? extends Container>> serverClasses;
	private Log log;

	private ThreadGroup group;
	private ProcessQueue queue;
	private ProcessQueue destroyerQueue;
	private SQLConnectionPool pool;
	private Vector<Variant> variants;
	private Hashtable<String, Integer> variantsMap;
	private Vector<Lobby> lobbies;

	private DirectoryWatcher swfBaseWatcher;
	private DirectoryWatcher sectionsCJWatcher;

	private Vector<String> badWords;

	protected Container() {

	}

	private boolean addBan(Connection user, long time, Set<BanType> type, BanRestriction restriction, String reason) {
		return addBan(-1, user, time, type, restriction, null, reason);
	}

	private boolean addBan(Connection user, long time, Set<BanType> type, BanRestriction restriction, String admin, String reason) {
		return addBan(-1, user, time, type, restriction, admin, reason);
	}

	private boolean addBan(Connection user, Set<BanType> type, BanRestriction restriction, String reason) {
		return addBan(-1, user, -1, type, restriction, null, reason);
	}

	private boolean addBan(int room, Connection user, long time, Set<BanType> type, BanRestriction restriction, String reason) {
		return addBan(room, user, time, type, restriction, null, reason);
	}

	private boolean addBan(int room, Connection user, long time, Set<BanType> type, BanRestriction restriction, String admin, String reason) {
		return send(() -> {
			InetAddress ip = user.getIP();
			try {
				Timestamp when = new Timestamp(System.currentTimeMillis());
				boolean result = executeTransaction((connection) -> connection.execute(SQLCommand.insert(DBV5Consts.TABLE_BANS, new Object[] { null, getGameName(), room == -1 ? null : room,
						user.getGZID(), user.getCompID(), ip != null ? ip.getHostAddress() : null, type, restriction, admin, when, time, reason, null, null, null })));
				return result;
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}

			return false;
		});
	}

	private boolean addBan(int room, String gzid, long time, Set<BanType> type, BanRestriction restriction, String admin, String reason) {
		return send(() -> {
			User user = getUserByGZID(gzid);
			String compid = user.getCompID();
			String ip = user.getIP();

			try {
				Timestamp when = new Timestamp(System.currentTimeMillis());
				boolean result = executeTransaction((connection) -> connection.execute(SQLCommand.insert(DBV5Consts.TABLE_BANS,
						new Object[] { null, getGameName(), room == -1 ? null : room, gzid, compid, ip, type, restriction, admin, when, time, reason, null, null, null })));
				return result;
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}

			return false;
		});
	}

	private boolean addBan(int room, String gzid, String compid, String ip, long time, Set<BanType> type, BanRestriction restriction, String admin, String reason) {
		return send(() -> {
			try {
				Timestamp when = new Timestamp(System.currentTimeMillis());
				boolean result = executeTransaction((connection) -> connection.execute(SQLCommand.insert(DBV5Consts.TABLE_BANS,
						new Object[] { null, getGameName(), room == -1 ? null : room, gzid, compid, ip, type, restriction, admin, when, time, reason, null, null, null })));
				return result;
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}

			return false;
		});
	}

	private boolean addBan(String gzid, long time, Set<BanType> type, BanRestriction restriction, String admin, String reason) {
		return addBan(-1, gzid, time, type, restriction, admin, reason);
	}

	public Session auth(String email, String password, String compid, String ip) {
		try {
			Session session = executeTransaction((connection) -> {
				Session result = null;
				User user = getUserByEmail(connection, email);
				if (user == null)
					return null;

				if (!user.isActive())
					return null;

				if (!password.equals(user.getPassword()))
					return null;

				user.compid = compid;

				String gzid = user.getGZID();

				boolean reusing = false;
				result = getSessionByGZID(gzid);
				if (result == null)
					result = new Session(user);
				else {
					result.user = user;
					result.created = new Timestamp(System.currentTimeMillis());
					reusing = true;
				}

				logCompID(connection, gzid, compid);
				logIP(connection, gzid, ip);

				try (ResultSet rs = connection.insert(DBV5Consts.TABLE_LOGIN_LOG, true, new Object[] { null, gzid, result.getCreated(), compid, ip })) {
					if (!rs.next())
						return null;

					int lastInsertID = rs.getInt(1);

					connection.update(DBV5Consts.TABLE_USERS, new String[] { "gzid" }, new Object[] { result.getGZID() }, new String[] { "compid", "last_login" }, new Object[] { compid, lastInsertID });

					if (!reusing)
						connection.insert(DBV5Consts.TABLE_SESSIONS, new Object[] { result.getFSID(), getGameName(), result.getGZID(), result.getCreated(), result.getExpires(), result.getExpires2() });
					else
						connection.update(DBV5Consts.TABLE_SESSIONS, new String[] { "fsid" }, new Object[] { result.getFSID() }, new String[] { "created" }, new Object[] { result.getCreated() });
				}

				return result;
			});

			if (session != null)
				sessions.put(session.getFSID(), session);

			return session;
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public boolean ban(Connection user, long time, Set<BanType> type, String reason) {
		return addBan(user, time, type, BanRestriction.ACCESS, reason);
	}

	public boolean ban(Connection user, long time, Set<BanType> type, String admin, String reason) {
		return addBan(user, time, type, BanRestriction.ACCESS, admin, reason);
	}

	public boolean ban(Connection user, long time, String reason) {
		return ban(user, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), reason);
	}

	public boolean ban(Connection user, long time, String admin, String reason) {
		return ban(user, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean ban(Connection user, Set<BanType> type, String reason) {
		return addBan(user, type, BanRestriction.ACCESS, reason);
	}

	public boolean ban(Connection user, String reason) {
		return ban(user, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), reason);
	}

	public boolean ban(int room, Connection user, long time, Set<BanType> type, String reason) {
		return addBan(room, user, time, type, BanRestriction.ACCESS, reason);
	}

	public boolean ban(int room, Connection user, long time, Set<BanType> type, String admin, String reason) {
		return addBan(room, user, time, type, BanRestriction.ACCESS, admin, reason);
	}

	public boolean ban(int room, String gzid, long time, Set<BanType> type, String admin, String reason) {
		return addBan(room, gzid, time, type, BanRestriction.ACCESS, admin, reason);
	}

	public boolean ban(int room, Connection user, long time, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), reason);
	}

	public boolean ban(int room, Connection user, long time, String admin, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean ban(String gzid, long time, Set<BanType> type, String admin, String reason) {
		return addBan(gzid, time, type, BanRestriction.ACCESS, admin, reason);
	}

	public boolean ban(String gzid, long time, String admin, String reason) {
		return ban(gzid, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean banByCompid(Connection user, long time, String reason) {
		return ban(user, time, EnumSet.of(BanType.COMPID), reason);
	}

	public boolean banByCompid(Connection user, long time, String admin, String reason) {
		return ban(user, time, EnumSet.of(BanType.COMPID), admin, reason);
	}

	public boolean banByCompid(Connection user, String reason) {
		return ban(user, EnumSet.of(BanType.COMPID), reason);
	}

	public boolean banByCompid(int room, Connection user, long time, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.COMPID), reason);
	}

	public boolean banByCompid(int room, Connection user, long time, String admin, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.COMPID), admin, reason);
	}

	public boolean banByCompidAndIP(Connection user, long time, String reason) {
		return ban(user, time, EnumSet.of(BanType.COMPID, BanType.IP), reason);
	}

	public boolean banByCompidAndIP(Connection user, long time, String admin, String reason) {
		return ban(user, time, EnumSet.of(BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean banByCompidAndIP(Connection user, String reason) {
		return ban(user, EnumSet.of(BanType.COMPID, BanType.IP), reason);
	}

	public boolean banByCompidAndIP(int room, Connection user, long time, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.COMPID, BanType.IP), reason);
	}

	public boolean banByCompidAndIP(int room, Connection user, long time, String admin, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean banByGZID(Connection user, long time, String reason) {
		return ban(user, time, EnumSet.of(BanType.GZID), reason);
	}

	public boolean banByGZID(Connection user, long time, String admin, String reason) {
		return ban(user, time, EnumSet.of(BanType.GZID), admin, reason);
	}

	public boolean banByGZID(Connection user, String reason) {
		return ban(user, EnumSet.of(BanType.GZID), reason);
	}

	public boolean banByGZID(int room, Connection user, long time, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.GZID), reason);
	}

	public boolean banByGZID(int room, Connection user, long time, String admin, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.GZID), admin, reason);
	}

	public boolean banByGZIDAndCompid(Connection user, long time, String reason) {
		return ban(user, time, EnumSet.of(BanType.GZID, BanType.COMPID), reason);
	}

	public boolean banByGZIDAndCompid(Connection user, long time, String admin, String reason) {
		return ban(user, time, EnumSet.of(BanType.GZID, BanType.COMPID), admin, reason);
	}

	public boolean banByGZIDAndCompid(Connection user, String reason) {
		return ban(user, EnumSet.of(BanType.GZID, BanType.COMPID), reason);
	}

	public boolean banByGZIDAndCompid(int room, Connection user, long time, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.GZID, BanType.COMPID), reason);
	}

	public boolean banByGZIDAndCompid(int room, Connection user, long time, String admin, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.GZID, BanType.COMPID), admin, reason);
	}

	public boolean banByGZIDAndIP(Connection user, long time, String reason) {
		return ban(user, time, EnumSet.of(BanType.GZID, BanType.IP), reason);
	}

	public boolean banByGZIDAndIP(Connection user, long time, String admin, String reason) {
		return ban(user, time, EnumSet.of(BanType.GZID, BanType.IP), admin, reason);
	}

	public boolean banByGZIDAndIP(Connection user, String reason) {
		return ban(user, EnumSet.of(BanType.GZID, BanType.IP), reason);
	}

	public boolean banByGZIDAndIP(int room, Connection user, long time, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.GZID, BanType.IP), reason);
	}

	public boolean banByGZIDAndIP(int room, Connection user, long time, String admin, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.GZID, BanType.IP), admin, reason);
	}

	public boolean banByIP(Connection user, long time, String reason) {
		return ban(user, time, EnumSet.of(BanType.IP), reason);
	}

	public boolean banByIP(String ip, long time, String reason) {
		return addBan(-1, null, null, ip, time, EnumSet.of(BanType.IP), BanRestriction.ACCESS, null, reason);
	}

	public boolean banByIP(Connection user, long time, String admin, String reason) {
		return ban(user, time, EnumSet.of(BanType.IP), admin, reason);
	}

	public boolean banByIP(Connection user, String reason) {
		return ban(user, EnumSet.of(BanType.IP), reason);
	}

	public boolean banByIP(int room, Connection user, long time, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.IP), reason);
	}

	public boolean banByIP(int room, Connection user, long time, String admin, String reason) {
		return ban(room, user, time, EnumSet.of(BanType.IP), admin, reason);
	}

	private Captcha buildCaptcha() {
		String vt1 = Character.toString(RandomUtil.randomArray(VT_CHARS));
		String vt2 = Character.toString(RandomUtil.randomArray(VT_CHARS));
		String vt3 = Character.toString(RandomUtil.randomArray(VT_CHARS));
		String vt = vt1 + vt2 + vt3;

		String vn = DigestUtil.md5(Long.toString(System.currentTimeMillis()));

		BufferedImage bufferedImage = new BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);

		Graphics2D g2d = bufferedImage.createGraphics();
		try {
			Font font = new Font("Verdana", Font.BOLD, 16);
			g2d.setFont(font);

			g2d.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);

			g2d.setColor(Color.red);
			g2d.drawString(vt1, 1, 18);

			g2d.setColor(Color.green);
			g2d.drawString(vt2, 14, 15);

			g2d.setColor(Color.blue);
			g2d.drawString(vt3, 28, 19);

			int pix[] = new int[CAPTCHA_WIDTH * CAPTCHA_HEIGHT];
			int k = 0;
			// for (int i = 0; i < CAPTCHA_WIDTH; i++)
			// for (int j = 0; j < CAPTCHA_HEIGHT; j++) {
			for (int j = 0; j < CAPTCHA_HEIGHT; j++)
				for (int i = 0; i < CAPTCHA_WIDTH; i++) {
					int rgb = bufferedImage.getRGB(i, j);
					pix[k++] = rgb;
				}

			HashMap<Integer, Integer> colors = new HashMap<>();
			String vs = printColor(pix[0]);
			colors.put(pix[0], 0);
			for (int i = 1; i < pix.length; i++) {
				int rgb = pix[i];
				Integer index = colors.get(rgb);
				String rgbstr;
				if (index == null) {
					colors.put(rgb, i);
					rgbstr = printColor(rgb);
				} else
					rgbstr = Integer.toString(index, 16);

				vs += " " + rgbstr;
			}

			try {
				Timestamp created = new Timestamp(System.currentTimeMillis());
				execute(SQLCommand.insert(DBV5Consts.TABLE_CAPTCHAS, new Object[] { vn, vs, vt, created }));
				return new Captcha(vn, vs, vt, created);
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}
		} finally {
			g2d.dispose();
		}

		return null;
	}

	private void checkClosed() {
		synchronized (this) {
			objectsClosed++;
		}

		logToOut("Destroyer queue closed.");

		if (pool != null) {
			pool.destroy();
			pool = null;
		}

		lobbies.clear();
		sessions.clear();
		ips.clear();
		loginAttempts.clear();

		if (tmrCleanupDatabase != null) {
			tmrCleanupDatabase.close();
			tmrCleanupDatabase = null;
		}

		try {
			AbandonedConnectionCleanupThread.shutdown();
		} catch (InterruptedException e) {
			logToErr("SEVERE problem cleaning up: " + e.getMessage(), e);
		}

		synchronized (log) {
			if (!log.isClosed()) {
				log.logToOut("Server log closed.");
				log.logToErr("Server error log closed.");
				log.close();
			}
		}

		closer.stopClosing();
	}

	private void cleanupDatabase() {
		synchronized (sessions) {
			ArrayList<String> fsids = new ArrayList<>(sessions.keySet());

			for (String fsid : fsids) {
				Session session = sessions.get(fsid);
				if (session.isExpired())
					sessions.remove(fsid);
			}
		}

		try {
			executeTransaction((connection) -> {
				connection.execute("DELETE FROM " + DBV5Consts.TABLE_SESSIONS + " WHERE expires != -1 AND DATE_ADD(created, INTERVAL expires / 1000 SECOND) <= NOW()");
				connection.execute("DELETE FROM " + DBV5Consts.TABLE_CAPTCHAS + " WHERE DATE_ADD(created, INTERVAL + " + DEFAULT_EXPIRES + " / 1000 SECOND) <= NOW()");
				loadBadWords(connection);
				return null;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
	}

	@Override
	public void close() {
		close(closer);
	}

	public void close(boolean wait) throws TimeOutException {
		close(wait, Closer.DEFAULT_CLOSE_TIMEOUT, false);
	}

	public void close(boolean wait, boolean force) throws TimeOutException {
		close(wait, Closer.DEFAULT_CLOSE_TIMEOUT, force);
	}

	public void close(boolean wait, int timeout) throws TimeOutException {
		close(wait, timeout, false);
	}

	public void close(boolean wait, int timeout, boolean force) throws TimeOutException {
		close(closer);
		logToOut("Waiting to close destroyer queue...");
		while (true) {
			int old = objectsClosed;
			try {
				destroyerQueue.waitForClose(timeout);

				break;
			} catch (TimeOutException e) {
				if (objectsClosed <= old) {
					logToErr("WARNING: Timeout to close destroyer queue.");
					if (force) {
						logToOut("Destroyer queue interrupted.");
						interrupt();
					}

					throw e;
				}
				old = objectsClosed;
			}
		}
	}

	public void close(Closer closer) {
		try {
			close(closer, false, Closer.DEFAULT_CLOSE_TIMEOUT, false);
		} catch (TimeOutException e) {
		}
	}

	public void close(Closer closer, boolean wait) throws TimeOutException {
		close(closer, wait, Closer.DEFAULT_CLOSE_TIMEOUT, false);
	}

	public void close(Closer closer, boolean wait, boolean force) throws TimeOutException {
		close(closer, wait, Closer.DEFAULT_CLOSE_TIMEOUT, force);
	}

	public void close(Closer closer, boolean wait, int timeout) throws TimeOutException {
		close(closer, wait, timeout, false);
	}

	public void close(Closer closer, boolean wait, int timeout, boolean force) throws TimeOutException {
		try {
			if (isRunningInDestroyerQueue())
				closer.close(wait, timeout, force);
			else if (wait) {
				TimeOutException exception = destroyerQueue.postAndWait(() -> {
					try {
						closer.close(wait, timeout, force);
					} catch (TimeOutException e) {
						return e;
					}

					return null;
				});
				if (exception != null)
					throw exception;
			} else
				destroyerQueue.post(() -> closer.close());
		} catch (InterruptedException e) {
		}
	}

	private void closeInternal() {
		swfBaseWatcher.close();
		sectionsCJWatcher.close();

		List<Lobby> lobbies;
		synchronized (this.lobbies) {
			for (Lobby lobby : this.lobbies)
				if (lobby.isOpen())
					objectsToClose += lobby.closingMaxScore();

			lobbies = lobbies();
		}

		for (Lobby lobby : lobbies)
			if (lobby.isOpen())
				lobby.stopAllGames(StopReason.CANCELED);

		objectsClosed = 0;

		for (Lobby lobby : lobbies) {
			if (!lobby.isOpen())
				continue;

			int oldObjectsClosed = objectsClosed;
			logToOut("Closing " + lobby + " ...");
			while (true) {
				int oldScore = 0;
				try {
					lobby.close(true);

					break;
				} catch (TimeOutException e) {
					int score = lobby.closingScore();
					if (score <= oldScore) {
						lobby.interrupt();
						logToErr("WARNING: The " + lobby + " was forced closed.");

						break;
					}
					oldScore = score;
				} finally {
					objectsClosed = oldObjectsClosed + lobby.closingScore();
				}
			}
			logToOut(lobby + " closed.");
		}

		logToOut("Closing queue...");
		try {
			queue.close(true);
			logToOut("Queue closed.");
		} catch (TimeOutException e) {
			queue.interrupt();
			logToOut("Queue interrupted.");
			logToErr("WARNING: The server queue was forced closed.");
		} finally {
			objectsClosed++;
		}

		destroyerQueue.close();
	}

	public int closingMaxScore() {
		return objectsToClose;
	}

	public int closingScore() {
		return objectsClosed;
	}

	private void createLogs(String logDir) throws IOException {
		File file = new File(logDir);
		if (!file.isAbsolute()) {
			file = new File(homeDir, logDir);
			logDir = file.getAbsolutePath();
		}

		if (!file.exists() && !file.mkdirs())
			throw new IOException("Could not create the server log directory " + logDir);

		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(System.currentTimeMillis());
		String s = DateTimeUtil.dateToStr_(calendar);
		log = new Log(new File(logDir, "server[" + s + "].txt"), new File(logDir, "errors[" + s + "].txt"));

		log.logToOut("Server log started for the game " + getGameName());
		log.logToErr("Server error log started for the game " + getGameName());
	}

	@SuppressWarnings("unchecked")
	@Override
	public void destroy() {
		try {
			close(true, true);
		} catch (TimeOutException e) {
			logToErr("ALERT", "Timeout on closing container.");
		}

		ServletContext context = getServletContext();

		context.removeAttribute(getGameName());

		ArrayList<Container> games = (ArrayList<Container>) context.getAttribute("games");
		if (games != null) {
			games.remove(this);
			if (games.size() == 0)
				context.removeAttribute("games");
		}

		System.gc();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;

		if (obj == null)
			return false;

		if (!(obj instanceof Container))
			return false;

		Container other = (Container) obj;
		String gameName = getGameName();
		if (gameName == null) {
			if (other.getGameName() != null)
				return false;
		} else if (!gameName.equals(other.getGameName()))
			return false;

		return true;
	}

	public boolean execute(String command) throws SQLException, InterruptedException {
		return pool != null ? pool.execute(command) : false;
	}

	public ResultSet execute2(String command) throws SQLException, InterruptedException {
		if (pool == null)
			throw new SQLException("Pool is null");

		return pool.execute2(command);
	}

	public ResultSet executeQuery(String command) throws SQLException, InterruptedException {
		if (pool == null)
			throw new SQLException("Pool is null");

		return pool.executeQuery(command);
	}

	public ResultSet executeQuery(String command, int resultSetType, int resultSetConcurrency) throws SQLException, InterruptedException {
		if (pool == null)
			throw new SQLException("Pool is null");

		return pool.executeQuery(command, resultSetType, resultSetConcurrency);
	}

	public <T> T executeStatsTransaction(String user, Transaction<T> transaction) throws SQLException, InterruptedException {
		return pool != null ? pool.executeTransaction((connection) -> {
			connection.lock(DBV5Consts.TABLE_STATS, user);
			try {
				return transaction.execute(connection);
			} finally {
				connection.unlock(DBV5Consts.TABLE_STATS, user);
			}
		}) : null;
	}

	public <T> T executeTransaction(Transaction<T> transaction) throws SQLException, InterruptedException {
		return pool != null ? pool.executeTransaction(transaction) : null;
	}

	public int executeUpdate(String command) throws SQLException, InterruptedException {
		return pool != null ? pool.executeUpdate(command) : 0;
	}

	public <T> T executeUserTransaction(String user, Transaction<T> transaction) throws SQLException, InterruptedException {
		return pool != null ? pool.executeTransaction((connection) -> {
			connection.lock(DBV5Consts.TABLE_USERS, user);
			try {
				return transaction.execute(connection);
			} finally {
				connection.unlock(DBV5Consts.TABLE_USERS, user);
			}
		}) : null;
	}

	public int getAdminLevel(int room, String user) {
		if (isSystem(user) || isRootAdmin(user))
			return 6;

		int level = getServerAdminLevel(user);
		if (level >= 0)
			return level;

		return getLobbyAdminLevel(room, user);
	}

	private long getBanExpires(int room, String typeName, Object typeValue, BanType type, BanRestriction restriction) {
		try {
			return executeTransaction((connection) -> {
				long result = 0;
				long max = 0;
				try (ResultSet rs = connection.executeQuery("SELECT * FROM " + DBV5Consts.TABLE_BANS + " WHERE " + "(game IS NULL OR game=" + SQLFormater.formatValue(getGameName()) + ") " + "AND "
						+ (room != -1 ? "(room IS NULL OR room=" + room + ") " : "room IS NULL ") + "AND " + typeName + "=" + SQLFormater.formatValue(typeValue) + " AND " + "(duration=-1 OR DATE_ADD("
						+ DBV5Consts.TABLE_BANS + ".when, INTERVAL duration / 1000 SECOND) > NOW()) AND unbanned_when IS NULL")) {
					long now = System.currentTimeMillis();
					while (rs.next()) {
						Set<BanType> type1 = asSet(BanType.class, rs.getString("type"));
						if (!type1.contains(type))
							continue;

						BanRestriction restriction1 = asEnum(BanRestriction.class, rs.getString("restriction"));
						if (!restriction1.equals(restriction))
							continue;

						Timestamp when = rs.getTimestamp("when");
						if (when == null)
							return -1L;

						long whenMillis = when.getTime();
						if (whenMillis > now)
							continue;

						long duration = rs.getLong("duration");
						if (duration == -1)
							return -1L;

						long t = whenMillis + duration - now;

						if (t > max) {
							max = t;
							result = t + now;
						}
					}
				}

				return result;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return -1;
	}

	public long getBanExpiresByCompid(int room, Connection user) {
		return getBanExpiresByCompid(room, user.getCompID());
	}

	public long getBanExpiresByCompid(int room, String compid) {
		return getBanExpires(room, "compid", compid, BanType.COMPID, BanRestriction.ACCESS);
	}

	public long getBanExpiresByCompidOrIP(int room, Connection user) {
		InetAddress addr = user.getIP();
		return getBanExpiresByCompidOrIP(room, user.getCompID(), addr != null ? addr.getHostAddress() : null);
	}

	public long getBanExpiresByCompidOrIP(int room, String compid, String ip) {
		long expires = getBanExpiresByCompid(room, compid);
		if (expires != 0)
			return expires;

		return getBanExpiresByIP(room, ip);
	}

	public long getBanExpiresByGZID(int room, Connection user) {
		return getBanExpiresByGZID(room, user.getGZID());
	}

	public long getBanExpiresByGZID(int room, String name) {
		return getBanExpires(room, "gzid", name, BanType.GZID, BanRestriction.ACCESS);
	}

	public long getBanExpiresByGZIDOrCompid(int room, Connection user) {
		return getBanExpiresByGZIDOrCompid(room, user.getGZID(), user.getCompID());
	}

	public long getBanExpiresByGZIDOrCompid(int room, String name, String compid) {
		long expires = getBanExpiresByGZID(room, name);
		if (expires != 0)
			return expires;

		return getBanExpiresByCompid(room, compid);
	}

	public long getBanExpiresByGZIDOrCompidOrIP(int room, Connection user) {
		InetAddress addr = user.getIP();
		return getBanExpiresByGZIDOrCompidOrIP(room, user.getGZID(), user.getCompID(), addr != null ? addr.getHostAddress() : null);
	}

	public long getBanExpiresByGZIDOrCompidOrIP(int room, String name, String compid, String ip) {
		long expires = getBanExpiresByGZID(room, name);
		if (expires != 0)
			return expires;

		expires = getBanExpiresByCompid(room, compid);
		if (expires != 0)
			return expires;

		return getBanExpiresByIP(room, ip);
	}

	public long getBanExpiresByGZIDOrIP(int room, Connection user) {
		InetAddress addr = user.getIP();
		return getBanExpiresByGZIDOrIP(room, user.getGZID(), addr != null ? addr.getHostAddress() : null);
	}

	public long getBanExpiresByGZIDOrIP(int room, String name, String ip) {
		long expires = getBanExpiresByGZID(room, name);
		if (expires != 0)
			return expires;
		return getBanExpiresByIP(room, ip);
	}

	public long getBanExpiresByIP(int room, Connection user) {
		InetAddress addr = user.getIP();
		return getBanExpiresByIP(room, addr != null ? addr.getHostAddress() : null);
	}

	public long getBanExpiresByIP(int room, String ip) {
		return getBanExpires(room, "ip", ip, BanType.IP, BanRestriction.ACCESS);
	}

	private Captcha getCaptcha(SQLConnection connection, String vn) throws SQLException {
		try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_CAPTCHAS, new String[] { "vn" }, new Object[] { vn })) {
			if (!rs.next())
				return null;

			Captcha result = new Captcha(rs);
			if (result.isExpired()) {
				connection.delete(DBV5Consts.TABLE_CAPTCHAS, new String[] { "vn" }, new Object[] { vn });
				return null;
			}

			return result;
		}
	}

	private Captcha getCaptcha(String vn) {
		try {
			return executeTransaction((connection) -> getCaptcha(connection, vn));
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public SQLConnection getConnection() throws InterruptedException, SQLException {
		return pool != null ? pool.getConnection() : null;
	}

	public SQLConnection getConnection(boolean releaseOnCompletion, boolean autoCommit) throws InterruptedException, SQLException {
		return pool != null ? pool.getConnection(releaseOnCompletion, autoCommit) : null;
	}

	protected abstract Class<? extends Connection> getConnectionClass();

	public String getContentURL() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("contenturl");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public String getDomainHost() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("domainhost");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public abstract String getGameName();

	public String getGameVersion() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("game_version");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	protected ThreadGroup getGroup() {
		return group;
	}

	public File getHomeDir() {
		return homeDir;
	}

	public String getHomeName() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("homename");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public String getForumURL() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("forumurl");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public String getHomeURL() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("homeurl");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public String getHomeVersion() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("home_version");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public int getInitialRating() {
		return INITIAL_RATING;
	}

	public abstract String getLastRelease();

	public int getLobbyAdminLevel(int room, String user) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_ADMINS, new String[] { "game", "room", "gzid" }, new Object[] { getGameName(), room, user }))) {
					if (rs.next())
						return rs.getInt("level");
				}

				return -1;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return -1;
	}

	public Lobby getLobbyByID(int id) {
		synchronized (lobbies) {
			for (Lobby lobby : lobbies)
				if (lobby.getID() == id)
					return lobby;

			return null;
		}
	}

	protected abstract Class<? extends Lobby> getLobbyClass();

	protected abstract Class<? extends LobbyGame> getLobbyGameClass();

	public Log getLog() {
		return log;
	}

	public String getLogDir() {
		return logDir;
	}

	public int getLossesAgainst(Connection player, Connection loser, int period) {
		return getLossesAgainst(player.getGZID(), loser.getGZID(), period);
	}

	public int getLossesAgainst(String playerGZID, String loserGZID, int period) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery("SELECT count(*) FROM " + DBV5Consts.TABLE_GAME_LOG + " WHERE " + "(" + "player1=" + SQLFormater.formatValue(playerGZID) + " AND player2="
						+ SQLFormater.formatValue(loserGZID) + " AND winner=1" + " OR " + "player1=" + SQLFormater.formatValue(loserGZID) + " AND player2=" + SQLFormater.formatValue(playerGZID)
						+ " AND winner=0" + ") " + "AND NOW() < DATE_ADD(played_when, INTERVAL " + period + " SECOND)")) {
					if (rs.next()) {
						int result = rs.getInt(1);
						return result;
					}
				}

				return 0;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return 0;
	}

	public int getMaxRating() {
		return MAX_RATING;
	}

	public int getMinRating() {
		return MIN_RATING;
	}

	public long getMuteExpiresByCompid(int room, Connection user) {
		return getMuteExpiresByCompid(room, user.getCompID());
	}

	public long getMuteExpiresByCompid(int room, String compid) {
		if (compid != null)
			return getBanExpires(room, "compid", compid, BanType.COMPID, BanRestriction.CHAT);

		return 0;
	}

	public long getMuteExpiresByCompidOrIP(int room, Connection user) {
		InetAddress addr = user.getIP();
		return getMuteExpiresByCompidOrIP(room, user.getCompID(), addr != null ? addr.getHostAddress() : null);
	}

	public long getMuteExpiresByCompidOrIP(int room, String compid, String ip) {
		long expires = getMuteExpiresByCompid(room, compid);
		if (expires != 0)
			return expires;

		return getMuteExpiresByIP(room, ip);
	}

	public long getMuteExpiresByGZID(int room, Connection user) {
		return getMuteExpiresByGZID(room, user.getGZID());
	}

	public long getMuteExpiresByGZID(int room, String gzid) {
		if (gzid != null)
			return getBanExpires(room, "gzid", gzid, BanType.GZID, BanRestriction.CHAT);

		return 0;
	}

	public long getMuteExpiresByGZIDOrCompid(int room, Connection user) {
		return getMuteExpiresByGZIDOrCompid(room, user.getGZID(), user.getCompID());
	}

	public long getMuteExpiresByGZIDOrCompid(int room, String name, String compid) {
		long expires = getMuteExpiresByGZID(room, name);
		if (expires != 0)
			return expires;

		return getMuteExpiresByCompid(room, compid);
	}

	public long getMuteExpiresByGZIDOrCompidOrIP(int room, Connection user) {
		InetAddress addr = user.getIP();
		return getMuteExpiresByGZIDOrCompidOrIP(room, user.getGZID(), user.getCompID(), addr != null ? addr.getHostAddress() : null);
	}

	public long getMuteExpiresByGZIDOrCompidOrIP(int room, String name, String compid, String ip) {
		long expires = getMuteExpiresByGZID(room, name);
		if (expires != 0)
			return expires;

		expires = getMuteExpiresByCompid(room, compid);
		if (expires != 0)
			return expires;

		return getMuteExpiresByIP(room, ip);
	}

	public long getMuteExpiresByGZIDOrIP(int room, Connection user) {
		InetAddress addr = user.getIP();
		return getMuteExpiresByGZIDOrIP(room, user.getGZID(), addr != null ? addr.getHostAddress() : null);
	}

	public long getMuteExpiresByGZIDOrIP(int room, String name, String ip) {
		long expires = getMuteExpiresByGZID(room, name);
		if (expires != 0)
			return expires;

		return getMuteExpiresByIP(room, ip);
	}

	public long getMuteExpiresByIP(int room, Connection user) {
		InetAddress addr = user.getIP();
		return getMuteExpiresByIP(room, addr.getHostAddress());
	}

	public long getMuteExpiresByIP(int room, String ip) {
		if (ip != null)
			return getBanExpires(room, "ip", ip, BanType.IP, BanRestriction.CHAT);

		return 0;
	}

	public int getOnlineUsers() {
		try {
			return executeTransaction((connection) -> {
				int online = 0;
				try (ResultSet rs = connection.executeQuery("SELECT count(*) FROM " + DBV5Consts.TABLE_SESSIONS + " WHERE expires2=-1 OR DATE_ADD(created, INTERVAL expires2 / 1000 SECOND) > NOW()")) {
					if (rs.next())
						online = rs.getInt(1);
				}

				return online;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return 0;
	}

	public ProcessQueue getQueue() {
		return queue;
	}

	public int getRegisteredUsers() {
		try {
			return executeTransaction((connection) -> getRegisteredUsers(connection));
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return 0;
	}

	protected int getRegisteredUsers(SQLConnection connection) {
		try {
			return executeTransaction((conn) -> {
				int registeredCount = 0;
				try (ResultSet rs = conn.executeQuery("SELECT count(*) FROM " + DBV5Consts.TABLE_USERS)) {
					if (rs.next())
						registeredCount = rs.getInt(1);
				}

				return registeredCount;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return 0;
	}

	public int getServerAdminLevel(String user) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_ADMINS, new String[] { "game", "room", "gzid" }, new Object[] { getGameName(), -1, user }))) {
					if (rs.next())
						return rs.getInt("level");
				}

				return -1;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return -1;
	}

	public String getServerURL() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("serverurl");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public Session getSession(String fsid) {
		Session session;
		synchronized (sessions) {
			session = sessions.get(fsid);
			if (session != null) {
				if (!session.isExpired())
					return session;

				sessions.remove(fsid);
			}
		}

		try {
			session = executeTransaction((connection) -> {
				Session result;

				try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_SESSIONS, new String[] { "fsid" }, new Object[] { fsid })) {
					if (!rs.next())
						return null;

					String gzid = rs.getString("gzid");

					User user = getUserByGZID(connection, gzid);

					result = new Session(user, rs);
				}

				if (!result.getFSID().equalsIgnoreCase(fsid))
					return null;

				if (result.isExpired()) {
					connection.delete(DBV5Consts.TABLE_SESSIONS, new String[] { "fsid" }, new Object[] { fsid });
					return null;
				}

				sessions.put(result.getGZID(), result);
				return result;
			});

			return session;
		} catch (InterruptedException e) {
		} catch (SQLException e) {
			handleException(e);
		}

		return null;
	}

	public Session getSessionByGZID(String gzid) {
		synchronized (sessions) {
			Set<String> fsids = sessions.keySet();
			ArrayList<String> sessionsToRemove = new ArrayList<>();
			try {
				for (String fsid : fsids) {
					Session s = sessions.get(fsid);
					if (s.isExpired())
						sessionsToRemove.add(fsid);
					else if (s.getGZID().equals(gzid))
						return s;
				}
			} finally {
				for (String fsid : sessionsToRemove)
					sessions.remove(fsid);
			}
		}

		try {
			Session session = executeTransaction((connection) -> {
				Session result;

				try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_SESSIONS, new String[] { "gzid" }, new Object[] { gzid })) {
					if (!rs.next())
						return null;

					User user = getUserByGZID(connection, gzid);

					result = new Session(user, rs);
				}

				if (result.isExpired()) {
					connection.delete(DBV5Consts.TABLE_SESSIONS, new String[] { "fsid" }, new Object[] { result.getFSID() });
					return null;
				}

				sessions.put(result.getGZID(), result);
				return result;
			});

			return session;
		} catch (InterruptedException e) {
		} catch (SQLException e) {
			handleException(e);
		}

		return null;
	}

	public String getSMenuVersion() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("smenu_version");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public String getSTV() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("swf_version");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public String getSWFDir() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("swfdir");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public String getTitle() {
		try (ResultSet rs = executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_GAMES, new String[] { "name" }, new Object[] { getGameName() }))) {
			if (rs.next()) {
				String result = rs.getString("title");
				return result;
			}
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public List<User> getTop100(String variant) {
		return getTop100(getVariant(variant));
	}

	public List<User> getTop100(Variant variant) {
		ArrayList<User> result = new ArrayList<>();

		try {
			executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery("SELECT * FROM " + DBV5Consts.TABLE_STATS + " LEFT JOIN " + DBV5Consts.TABLE_USERS + " ON " + DBV5Consts.TABLE_STATS + ".gzid = "
						+ DBV5Consts.TABLE_USERS + ".gzid WHERE game=" + SQLFormater.formatValue(getGameName()) + " AND variant=" + SQLFormater.formatValue(variant.getName())
						+ " AND rating > 0 AND active ORDER BY rating DESC, playeds DESC, wins DESC LIMIT 100")) {
					while (rs.next()) {
						String gzid = rs.getString(DBV5Consts.TABLE_STATS + ".gzid");
						User user = getUserByGZID(connection, gzid);
						if (user != null)
							result.add(user);
					}
				}

				return null;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return result;
	}

	protected User getUserByEmail(SQLConnection connection, String email) throws SQLException {
		try (ResultSet rs = connection.executeQuery(makeUsersJoinByEmail(email))) {
			if (!rs.next())
				return null;

			User result = new User(rs);
			String gzid = result.getGZID();
			try (ResultSet rs1 = connection.selectAll(DBV5Consts.TABLE_STATS, new String[] { "game", "gzid" }, new Object[] { getGameName(), gzid })) {
				ArrayList<Variant> remainingVariants = new ArrayList<>(variants);
				while (rs1.next()) {
					String variantName = rs1.getString("variant");
					Variant variant = getVariant(variantName);
					if (variant == null)
						continue;

					remainingVariants.remove(variant);
					result.updateStatsFromStatsTable(variant, rs1);
				}

				for (Variant variant : remainingVariants) {
					result.updateStats(variant, new UserStats(variant, gzid));
					post(() -> {
						try {
							executeTransaction((conn) -> conn.execute(SQLCommand.insert(DBV5Consts.TABLE_STATS, true, new Object[] { getGameName(), variant.getName(), gzid, 0, 0, 0, 0, 0, 0, 0, 0 })));
						} catch (SQLException e) {
							handleException(e);
						} catch (InterruptedException e) {
						}
					});
				}
			}

			try (ResultSet rs1 = connection.selectAll(DBV5Consts.TABLE_PREMIUMS, new String[] { "gzid" }, new Object[] { gzid })) {
				if (rs1.next()) {
					PremiumData premiumData = new PremiumData(gzid, rs1);
					if (premiumData.isExpired()) {
						connection.delete(DBV5Consts.TABLE_PREMIUMS, new String[] { "gzid" }, new Object[] { gzid });
						result.updatePremiumData(null);
					} else
						result.updatePremiumData(premiumData);
				}
			}

			try (ResultSet rs1 = connection.selectAll(DBV5Consts.TABLE_INVENTORIES, new String[] { "gzid" }, new Object[] { gzid })) {
				while (rs1.next()) {
					Inventory inventory = new Inventory(rs1);
					List<Inventory> inventories = result.inventories.get(inventory.getType());
					if (inventories == null) {
						inventories = new ArrayList<Inventory>();
						result.inventories.put(inventory.getType(), inventories);
					}

					inventories.add(inventory);
				}
			}

			result.group = getGroupContainingUser(connection, gzid);

			return result;
		}
	}

	public User getUserByEmail(String email) {
		try {
			return executeTransaction((connection) -> getUserByEmail(connection, email));
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	protected User getUserByGZID(SQLConnection connection, String gzid) throws SQLException {
		try (ResultSet rs = connection.executeQuery(makeUsersJoinByGZID(gzid))) {
			if (!rs.next())
				return null;

			User result = new User(rs);
			try (ResultSet rs1 = connection.selectAll(DBV5Consts.TABLE_STATS, new String[] { "game", "gzid" }, new Object[] { getGameName(), gzid })) {
				ArrayList<Variant> remainingVariants = new ArrayList<>(variants);
				while (rs1.next()) {
					String variantName = rs1.getString("variant");
					Variant variant = getVariant(variantName);
					if (variant == null)
						continue;

					remainingVariants.remove(variant);
					result.updateStatsFromStatsTable(variant, rs1);
				}

				for (Variant variant : remainingVariants) {
					result.updateStats(variant, new UserStats(variant, gzid));
					post(() -> {
						try {
							executeTransaction((conn) -> conn.execute(SQLCommand.insert(DBV5Consts.TABLE_STATS, true, new Object[] { getGameName(), variant.getName(), gzid, 0, 0, 0, 0, 0, 0, 0, 0 })));
						} catch (SQLException e) {
							handleException(e);
						} catch (InterruptedException e) {
						}
					});
				}
			}

			try (ResultSet rs1 = connection.selectAll(DBV5Consts.TABLE_PREMIUMS, new String[] { "gzid" }, new Object[] { gzid })) {
				if (rs1.next()) {
					PremiumData premiumData = new PremiumData(gzid, rs1);
					if (premiumData.isExpired()) {
						connection.delete(DBV5Consts.TABLE_PREMIUMS, new String[] { "gzid" }, new Object[] { gzid });
						result.updatePremiumData(null);
					} else
						result.updatePremiumData(premiumData);
				}
			}

			try (ResultSet rs1 = connection.selectAll(DBV5Consts.TABLE_INVENTORIES, new String[] { "gzid" }, new Object[] { gzid })) {
				while (rs1.next()) {
					Inventory inventory = new Inventory(rs1);
					List<Inventory> inventories = result.inventories.get(inventory.getType());
					if (inventories == null) {
						inventories = new ArrayList<Inventory>();
						result.inventories.put(inventory.getType(), inventories);
					}

					inventories.add(inventory);
				}
			}

			result.group = getGroupContainingUser(connection, gzid);

			return result;
		}
	}

	public Group getGroupFromOwner(String gzid) {
		try {
			return executeTransaction((connection) -> getGroupFromOwner(connection, gzid));
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	private Group getGroupFromOwner(SQLConnection connection, String owner) throws SQLException {
		try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_GROUPS, new String[] { "owner" }, new Object[] { owner })) {
			if (rs.next()) {
				Group group = new Group(rs);
				return group;
			}
		}

		return null;
	}

	public Group getGroupFromID(String groupID) {
		try {
			return executeTransaction((connection) -> getGroupFromID(connection, groupID));
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	private Group getGroupFromID(SQLConnection connection, String groupID) throws SQLException {
		try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_GROUPS, new String[] { "id" }, new Object[] { groupID })) {
			if (rs.next()) {
				Group group = new Group(rs);
				return group;
			}
		}

		return null;
	}

	public User getUserByGZID(String gzid) {
		try {
			return executeTransaction((connection) -> getUserByGZID(connection, gzid));
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	public List<Connection> getUsersByGZID(String gzid) {
		List<Connection> result = new ArrayList<>();

		List<Lobby> lobbies = lobbies();
		for (Lobby lobby : lobbies) {
			if (!lobby.isOpen())
				continue;

			Connection user = lobby.getUserByGZID(gzid);
			if (user != null)
				result.add(user);

			List<LobbyGame> tables = lobby.games(gzid);
			for (LobbyGame table : tables) {
				user = table.getUserByGZID(gzid);
				if (user != null)
					result.add(user);
			}
		}

		return result;
	}

	public Variant getVariant(String name) {
		Integer index = variantsMap.get(name);
		if (index == null)
			return null;

		return variants.get(index);
	}

	public abstract String getVersion();

	public int getWinsAgainst(Connection player, Connection loser, int period) {
		return getWinsAgainst(player.getGZID(), loser.getGZID(), loser.getCompID(), loser.getIP().getHostAddress(), period);
	}

	public int getWinsAgainst(String playerGZID, String loserGZID, String loserCompidID, String loserIP, int period) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery(
						"SELECT count(*) FROM " + DBV5Consts.TABLE_GAME_LOG + " WHERE " + "(" + "player1=" + SQLFormater.formatValue(playerGZID) + " AND (player2=" + SQLFormater.formatValue(loserGZID)
								+ " OR player2_compid=" + SQLFormater.formatValue(loserCompidID) + " OR player2_ip=" + SQLFormater.formatValue(loserIP) + ") AND winner=0" + " OR " + "player2="
								+ SQLFormater.formatValue(playerGZID) + " AND (player1=" + SQLFormater.formatValue(loserGZID) + " OR player1_compid=" + SQLFormater.formatValue(loserCompidID)
								+ " OR player1_ip=" + SQLFormater.formatValue(loserIP) + ") AND winner=1" + ") " + "AND NOW() <= DATE_ADD(played_when, INTERVAL " + period + " SECOND)")) {
					if (rs.next()) {
						int result = rs.getInt(1);
						return result;
					}
				}

				return 0;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return 0;
	}

	protected void handleException(Connection connection, Throwable e) {
		if (e instanceof IOException)
			handleIOException(connection, (IOException) e);
		else
			logToErr(connection.toString(), e);
	}

	protected void handleException(Session session, Throwable e) {
		if (e instanceof IOException)
			handleIOException(session, (IOException) e);
		else
			logToErr(session != null ? session.toString() : null, e);
	}

	protected void handleException(Throwable e) {
		if (e instanceof IOException)
			handleIOException((IOException) e);
		else
			logToErr(e);
	}

	protected void handleIOException(Connection connection, IOException e) {
		if (e instanceof EOFException)
			return;

		if (e instanceof SocketTimeoutException)
			return;

		logToErr(connection.toString(), e);
	}

	protected void handleIOException(IOException e) {
		if (e instanceof EOFException)
			return;

		if (e instanceof SocketTimeoutException)
			return;

		logToErr(e);
	}

	protected void handleIOException(Session session, IOException e) {
		if (e instanceof EOFException)
			return;

		if (e instanceof SocketTimeoutException)
			return;

		logToErr(session != null ? session.toString() : null, e);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		String gameName = getGameName();
		result = prime * result + (gameName == null ? 0 : gameName.hashCode());
		return result;
	}

	public void incrementDraws(String variant, String user) {
		incrementDraws(variant, user, 0, -1);
	}

	public void incrementDraws(String variant, String user, int dr, int rating2) {
		try {
			executeTransaction((connection) -> {
				return connection.update(DBV5Consts.TABLE_STATS, new String[] { "game", "variant", "gzid" }, new Object[] { getGameName(), variant, user },
						rating2 != -1 ? new String[] { "playeds", "draws", "streak", "rating", "rating2" } : new String[] { "playeds", "draws", "streak" },
						rating2 != -1 ? new Object[] { new SQLExpression("playeds + 1"), new SQLExpression("draws + 1"), 0, new SQLExpression("rating + " + dr), 0, rating2 }
								: new Object[] { new SQLExpression("playeds + 1"), new SQLExpression("draws + 1"), 0 });
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
	}

	public void incrementLosses(String variant, String user, boolean abandoned) {
		incrementLosses(variant, user, abandoned, 0, -1);
	}

	public void incrementLosses(String variant, String user, boolean abandoned, int dr, int rating2) {
		try {
			executeTransaction((connection) -> {
				return connection.update(DBV5Consts.TABLE_STATS, new String[] { "game", "variant", "gzid" }, new Object[] { getGameName(), variant, user },
						rating2 != -1 ? new String[] { "playeds", "losses", "abandoneds", "streak", "rating", "rating2" } : new String[] { "playeds", "losses", "abandoneds", "streak" },
						rating2 != -1
								? new Object[] { new SQLExpression("playeds + 1"), new SQLExpression("losses + 1"), new SQLExpression("abandoneds + " + (abandoned ? "1" : "0")),
										new SQLExpression("if(streak <= -1, streak - 1, -1)"), new SQLExpression("rating + " + dr), rating2 }
								: new Object[] { new SQLExpression("playeds + 1"), new SQLExpression("losses + 1"), new SQLExpression("abandoneds + " + (abandoned ? "1" : "0")),
										new SQLExpression("if(streak <= -1, streak - 1, -1)") });
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
	}

	public void incrementWins(String variant, String user) {
		incrementWins(variant, user, 0, -1);
	}

	public void incrementWins(String variant, String user, int dr, int rating2) {
		try {
			executeTransaction((connection) -> {
				return connection.update(DBV5Consts.TABLE_STATS, new String[] { "game", "variant", "gzid" }, new Object[] { getGameName(), variant, user },
						rating2 != -1 ? new String[] { "playeds", "wins", "streak", "rating", "rating2" } : new String[] { "playeds", "wins", "streak" },
						rating2 != -1
								? new Object[] { new SQLExpression("playeds + 1"), new SQLExpression("wins + 1"), new SQLExpression("if(streak >= 1, streak + 1, 1)"),
										new SQLExpression("rating + " + dr), rating2 }
								: new Object[] { new SQLExpression("playeds + 1"), new SQLExpression("wins + 1"), new SQLExpression("if(streak >= 1, streak + 1, 1)") });
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void init() {
		if (closer != null && closer.isOpen())
			return;

		String homeDirStr = System.getenv("GZV5_HOME");
		if (homeDirStr == null)
			homeDir = new File(DEFAULT_HOME_DIR);
		else
			homeDir = new File(homeDirStr);

		if (!homeDir.exists()) {
			logToErr("home directory not exist: " + homeDir);
			return;
		}

		File configFile = new File(homeDir, "config.xml");
		try {
			config = parseConfig(configFile);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			handleException(e);
			return;
		}

		serverClasses = new HashMap<>();
		parseServerConfigs();

		homeDir = new File(homeDir, getGameName());
		if (!homeDir.exists() && !homeDir.mkdir()) {
			logToErr("Could not create the server directory " + homeDir);
			return;
		}

		objectsClosed = 0;
		logDir = null;

		closer = new Closer(new Interruptable() {

			@Override
			public void close() {
				closeInternal();
			}

			@Override
			public void interrupt() {
				Container.this.interrupt();
			}

		});

		String gameName = getGameName();

		int dbMaxPoolLength = SQLConnectionPool.DEFAULT_POOL_LENGTH;
		String dbDriverName = DBV5Consts.DEFAULT_DB_DRIVER_NAME;
		String dbDriverClass = DBV5Consts.DEFAULT_DB_DRIVER_CLASS;
		String dbHost = DBV5Consts.DEFAULT_DB_HOST;
		int dbPort = DBV5Consts.DEFAULT_DB_PORT;
		String dbName = DBV5Consts.DEFAULT_DB_NAME;
		String dbUserName = DBV5Consts.DEFAULT_DB_USERNAME;
		String dbPassword = DBV5Consts.DEFAULT_DB_PASSWORD;

		for (int i = 0; i < config.getChildCount(); i++) {
			Tree.Node<ConfigEntry> child = config.getChild(i);
			ConfigEntry entry = child.getValue();
			if (!(entry instanceof MapConfigEntry))
				continue;

			String name = entry.getName();
			Map<String, String> attrs = ((MapConfigEntry) entry).map();
			if (name.equalsIgnoreCase("log") && logDir == null) {
				logDir = attrs.getOrDefault("path", new File(homeDir, "logs").getAbsolutePath());
				try {
					createLogs(logDir);
				} catch (IOException e) {
					handleException(e);
					return;
				}
			} else if (name.equals("db")) {
				String s = attrs.get("max_pool_length");
				try {
					dbMaxPoolLength = s != null ? Integer.parseInt(s) : SQLConnectionPool.DEFAULT_POOL_LENGTH;
				} catch (NumberFormatException e) {
					dbMaxPoolLength = SQLConnectionPool.DEFAULT_POOL_LENGTH;
				}

				dbDriverName = attrs.getOrDefault("driver_name", DBV5Consts.DEFAULT_DB_DRIVER_NAME);
				dbDriverClass = attrs.getOrDefault("driver_class", DBV5Consts.DEFAULT_DB_DRIVER_CLASS);
				dbHost = attrs.getOrDefault("host", DBV5Consts.DEFAULT_DB_HOST);

				s = attrs.get("port");
				try {
					dbPort = s != null ? Integer.parseInt(s) : DBV5Consts.DEFAULT_DB_PORT;
				} catch (NumberFormatException e) {
					dbPort = DBV5Consts.DEFAULT_DB_PORT;
				}

				dbName = attrs.getOrDefault("name", DBV5Consts.DEFAULT_DB_NAME);
				dbUserName = attrs.getOrDefault("username", DBV5Consts.DEFAULT_DB_USERNAME);
				dbPassword = attrs.getOrDefault("password", DBV5Consts.DEFAULT_DB_PASSWORD);
			}
		}

		if (logDir == null)
			try {
				createLogs(new File(homeDir, "logs").getAbsolutePath());
			} catch (IOException e) {
				handleException(e);
				return;
			}

		sessions = new Hashtable<>();
		ips = new Hashtable<>();
		loginAttempts = new Hashtable<>();
		variants = new Vector<>();
		variantsMap = new Hashtable<>();
		lobbies = new Vector<>();
		badWords = new Vector<>();

		try {
			pool = new SQLConnectionPool(dbMaxPoolLength, dbDriverName, dbDriverClass, dbHost, dbPort, dbName, dbUserName, dbPassword);
		} catch (ClassNotFoundException e) {
			handleException(e);
			return;
		}

		group = new ThreadGroup(gameName);

		objectsToClose = 2;

		destroyerQueue = new ProcessQueue(group, gameName + " destroyer queue");
		destroyerQueue.addExceptionListener((e) -> handleException(e));
		destroyerQueue.addCloseListener(() -> checkClosed());

		queue = new ProcessQueue(group, gameName + " server queue");
		queue.addExceptionListener((e) -> handleException(e));

		ServletContext context = getServletContext();
		String path = context.getRealPath(context.getContextPath() + "/swf_v5/base");
		try {
			swfBaseWatcher = new DirectoryWatcher(group, path, new DirectoryWatcherListener() {

				@Override
				public void onModify(File file) {
					logToOut("swf_v5/base", "File " + file + " was been modified");
				}

				@Override
				public void onException(Throwable e) {
					if (!(e instanceof InterruptedException))
						handleException(e);
				}

				@Override
				public void onDelete(File file) {
					// logToOut("swf_v5/base", "File " + file + " was been
					// deleted");
				}

				@Override
				public void onCreate(File file) {
					// logToOut("swf_v5/base", "File " + file + " was been
					// created");
				}
			});
		} catch (IOException e) {
			handleException(e);
			return;
		}

		try {
			sectionsCJWatcher = new DirectoryWatcher(group, path + "/sections_cj", new DirectoryWatcherListener() {

				@Override
				public void onModify(File file) {
					logToOut("swf_v5/base/sections_cj", "File " + file + " was been modified");
				}

				@Override
				public void onException(Throwable e) {
					if (!(e instanceof InterruptedException))
						handleException(e);
				}

				@Override
				public void onDelete(File file) {
					// logToOut("swf_v5/base/sections_cj", "File " + file + "
					// was been deleted");
				}

				@Override
				public void onCreate(File file) {
					// logToOut("swf_v5/base/sections_cj", "File " + file + "
					// was been created");
				}
			});
		} catch (IOException e) {
			swfBaseWatcher.close();
			handleException(e);
			return;
		}

		Throwable exception;
		try {
			exception = queue.postAndWait(() -> {
				try {
					return executeTransaction((connection) -> {
						try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_VARIANTS, new String[] { "game" }, new Object[] { getGameName() })) {
							while (rs.next()) {
								String name = rs.getString("name");
								String title = rs.getString("title");

								Variant variant = new Variant(name, title);
								variants.add(variant);
								variantsMap.put(name, variants.size() - 1);
							}
						}

						try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_ROOMS, new String[] { "game" }, new Object[] { getGameName() }, "id")) {
							while (rs.next()) {
								int id = rs.getInt("id");
								String name = rs.getString("name");
								String country = rs.getString("country");
								String groupID = rs.getString("group");
								String variant = rs.getString("variant");
								String tournamentID = rs.getString("tournament");
								int maxPlayers = rs.getShort("max_players");
								String lobbyHost = rs.getString("lobby_host");
								int lobbyPort = rs.getInt("lobby_port");
								String gameHost = rs.getString("game_host");
								int gamePort = rs.getInt("game_port");
								boolean active = rs.getBoolean("active");

								addLobbyInternal(id, name, country, groupID, variant, tournamentID, maxPlayers, lobbyHost, lobbyPort, gameHost, gamePort, active);
							}
						}

						return null;
					});
				} catch (Throwable e) {
					return e;
				}
			});
		} catch (InterruptedException e) {
			swfBaseWatcher.close();
			sectionsCJWatcher.close();
			handleException(e);
			return;
		}

		if (exception != null) {
			swfBaseWatcher.close();
			sectionsCJWatcher.close();
			handleException(exception);
			return;
		}

		tmrCleanupDatabase = new Timer(queue, CLEANUP_INTERVAL, true, (e) -> handleException(e));
		tmrCleanupDatabase.addListener((timer, interval) -> cleanupDatabase());

		cleanupDatabase();
		tmrCleanupDatabase.play();

		context.setAttribute(getGameName(), this);

		ArrayList<Container> games = (ArrayList<Container>) context.getAttribute("games");
		if (games == null) {
			games = new ArrayList<>();
			context.setAttribute("games", games);
		}

		games.add(this);
	}

	public void addPublicLobby(String name, String lobbyHost, int lobbyPort) {
		addLobby(name, null, null, null, null, 120, lobbyHost, lobbyPort, lobbyHost, lobbyPort + 100, true);
	}

	public void addPublicLobby(String name, String lobbyHost, int lobbyPort, String gameHost, int gamePort) {
		addLobby(name, null, null, null, null, 120, lobbyHost, lobbyPort, gameHost, gamePort, true);
	}

	public void addNationalLobby(String name, String country, String lobbyHost, int lobbyPort) {
		addLobby(name, country, null, null, null, 120, lobbyHost, lobbyPort, lobbyHost, lobbyPort + 100, true);
	}

	public void addNationalLobby(String name, String country, String lobbyHost, int lobbyPort, String gameHost, int gamePort) {
		addLobby(name, country, null, null, null, 120, lobbyHost, lobbyPort, gameHost, gamePort, true);
	}

	public void addGroupLobby(String name, String groupID, String lobbyHost, int lobbyPort) {
		addLobby(name, null, groupID, null, null, 120, lobbyHost, lobbyPort, lobbyHost, lobbyPort + 100, true);
	}

	public void addGroupLobby(String name, String groupID, String lobbyHost, int lobbyPort, String gameHost, int gamePort) {
		addLobby(name, null, groupID, null, null, 120, lobbyHost, lobbyPort, gameHost, gamePort, true);
	}

	public void addLobbyByType(String name, String variant, String lobbyHost, int lobbyPort) {
		addLobby(name, null, null, variant, null, 120, lobbyHost, lobbyPort, lobbyHost, lobbyPort + 100, true);
	}

	public void addLobbyByType(String name, String variant, String lobbyHost, int lobbyPort, String gameHost, int gamePort) {
		addLobby(name, null, null, variant, null, 120, lobbyHost, lobbyPort, gameHost, gamePort, true);
	}

	public void addTournamentLobby(String name, String tournamentID, String lobbyHost, int lobbyPort) {
		addLobby(name, null, null, null, tournamentID, 120, lobbyHost, lobbyPort, lobbyHost, lobbyPort + 100, true);
	}

	public void addTournamentLobby(String name, String tournamentID, String lobbyHost, int lobbyPort, String gameHost, int gamePort) {
		addLobby(name, null, null, null, tournamentID, 120, lobbyHost, lobbyPort, gameHost, gamePort, true);
	}

	public void addLobby(String name, String country, String groupID, String variant, String tournamentID, int maxPlayers, String lobbyHost, int lobbyPort, String gameHost, int gamePort,
			boolean active) {
		post(() -> {
			Group group = getGroupFromID(groupID);

			try {
				int id = executeTransaction((connection) -> {
					try (ResultSet rs = connection.insert(DBV5Consts.TABLE_ROOMS, true,
							new Object[] { null, getGameName(), name, country, groupID, variant, tournamentID, lobbyHost, lobbyPort, gameHost, gamePort, maxPlayers, 0, active })) {
						if (!rs.next())
							return -1;

						int lastInsertID = rs.getInt(1);

						if (group != null)
							connection.insert(DBV5Consts.TABLE_ADMINS, new Object[] { null, getGameName(), lastInsertID, group.getOwner(), 6, null, null, true });

						return lastInsertID;
					}
				});

				if (id != -1)
					addLobbyInternal(id, name, country, groupID, variant, tournamentID, maxPlayers, lobbyHost, lobbyPort, gameHost, gamePort, active);
			} catch (SQLException e) {
				handleException(e);
			} catch (InterruptedException e) {
			}
		});
	}

	protected void addLobbyInternal(int id, String name, String country, String groupID, String variant, String tournamentID, int maxPlayers, String lobbyHost, int lobbyPort, String gameHost,
			int gamePort, boolean active) {
		Lobby lobby;
		try {
			lobby = getLobbyClass().newInstance();
		} catch (Exception e) {
			logToErr("Could not create the lobby instance", e);
			return;
		}

		try {
			if (active)
				lobby.open(this, id, name, country, groupID, variant, tournamentID, maxPlayers, lobbyHost, lobbyPort, gameHost, gamePort);

			lobbies.add(lobby);
		} catch (Throwable e) {
			handleException(e);
		}
	}

	public void activateLobby(int id) {
		setLobbyActive(id, true);
	}

	public void deactivateLobby(int id) {
		setLobbyActive(id, false);
	}

	private void setLobbyActive(int id, boolean active) {
		Lobby lobby = getLobbyByID(id);
		if (lobby == null)
			return;

		post(() -> {
			try {
				executeTransaction((connection) -> {
					connection.update(DBV5Consts.TABLE_ROOMS, new String[] { "id" }, new Object[] { id }, new String[] { "active" }, new Object[] { active });
					return null;
				});

				if (!active)
					lobby.close();
				else
					lobby.open();
			} catch (InterruptedException e) {
			} catch (Throwable e) {
				handleException(e);
			}
		});
	}

	@Override
	public void interrupt() {
		group.interrupt();

		logToErr("WARNING: All threads were interrupted.");
	}

	public boolean isRootAdmin(String gzid) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery("SELECT * FROM " + DBV5Consts.TABLE_ADMINS + " WHERE game IS NULL AND room IS NULL AND active=1 AND gzid=" + SQLFormater.formatValue(gzid))) {
					return rs.next();
				}
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return false;
	}

	public boolean isGameLevelAdmin(String game, String gzid) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_ADMINS, new String[] { "game", "room", "gzid" }, new Object[] { game, null, gzid }))) {
					return rs.next();
				}
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return false;
	}

	public boolean isLobbyLevelAdmin(String game, int lobby, String gzid) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_ADMINS, new String[] { "game", "room", "gzid" }, new Object[] { game, lobby, gzid }))) {
					return rs.next();
				}
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return false;
	}

	public boolean isClosed() {
		return closer.isClosed();
	}

	public boolean isClosing() {
		return closer.isClosing();
	}

	public boolean isMuted(int lobby, Connection connection) {
		return isMuted(lobby, connection.getGZID(), connection.getCompID(), connection.getIP().getHostAddress());
	}

	public boolean isMuted(int lobby, String gzid, String compID, String ip) {
		long time = getMuteExpiresByGZIDOrCompidOrIP(lobby, gzid, compID, ip);
		return time > 0;
	}

	public boolean isRunningInDestroyerQueue() {
		return destroyerQueue.isCurrentThread();
	}

	public boolean isSystem(String user) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery(SQLCommand.selectAll(DBV5Consts.TABLE_USERS, new String[] { "gzid" }, new Object[] { user }))) {
					if (rs.next())
						return rs.getBoolean("system");
				}

				return false;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return false;
	}

	private void loadTree(NodeList childs, Tree.Node<ConfigEntry> node) {
		for (int i = 0; i < childs.getLength(); i++) {
			Node child = childs.item(i);
			NamedNodeMap attrs = child.getAttributes();
			Tree.Node<ConfigEntry> node1;
			if (attrs != null) {
				Map<String, String> map = new HashMap<>();
				for (int j = 0; j < attrs.getLength(); j++) {
					Node attr = attrs.item(j);
					map.put(attr.getNodeName(), attr.getNodeValue());
				}
				String value = child.getNodeValue();
				if (value != null)
					map.put("value", value);
				node1 = new Tree.Node<>(new MapConfigEntry(child.getNodeName(), map));
			} else
				node1 = new Tree.Node<>(new SimpleConfigEntry(child.getNodeName(), child.getNodeValue()));
			node.addChild(node1);
			NodeList childs1 = child.getChildNodes();
			loadTree(childs1, node1);
		}
	}

	protected void logCompID(Session session, String compid) {
		try {
			executeTransaction((connection) -> {
				logCompID(connection, session, compid);
				return null;
			});
		} catch (SQLException e) {
			handleException(session, e);
		} catch (InterruptedException e) {
		}
	}

	protected void logCompID(SQLConnection connection, Session session, String compid) throws SQLException {
		logCompID(connection, session.getGZID(), compid);
	}

	protected void logCompID(SQLConnection connection, String gzid, String compid) throws SQLException {
		if (!connection.execute(SQLCommand.insert(DBV5Consts.TABLE_COMPIDS, true, new Object[] { compid, 1, gzid, new Timestamp(System.currentTimeMillis()), false, null, null, null })))
			connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_COMPIDS + " SET times_used=times_used+1, last_time_used_by=" + SQLFormater.formatValue(gzid) + ", last_time_used_when="
					+ SQLFormater.formatValue(new Timestamp(System.currentTimeMillis())) + " WHERE compid=" + SQLFormater.formatValue(compid) + " AND last_time_used_by<>"
					+ SQLFormater.formatValue(gzid));
	}

	protected void logEmail(Session session, String email) {
		try {
			executeTransaction((connection) -> {
				logEmail(connection, session, email);
				return null;
			});
		} catch (SQLException e) {
			handleException(session, e);
		} catch (InterruptedException e) {
		}
	}

	protected void logEmail(SQLConnection connection, Session session, String email) throws SQLException {
		logEmail(connection, session.getGZID(), email);
	}

	protected void logEmail(SQLConnection connection, String gzid, String email) throws SQLException {
		if (!connection.execute(SQLCommand.insert(DBV5Consts.TABLE_EMAILS, true, new Object[] { email, 1, 1, gzid, new Timestamp(System.currentTimeMillis()), false, null, null, null })))
			connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_EMAILS + " SET times_used=times_used+1, last_time_used_by=" + SQLFormater.formatValue(gzid) + ", last_time_used_when="
					+ SQLFormater.formatValue(new Timestamp(System.currentTimeMillis())) + " WHERE email=" + SQLFormater.formatValue(email) + " AND last_time_used_by<>"
					+ SQLFormater.formatValue(gzid));
	}

	public void logGameResult(String variant, Lobby lobby, boolean rated, Connection player1, int oldRating1, int oldRating12, Connection player2, int oldRating2, int oldRating22, long startTime,
			int turns, int winner) {
		try {
			executeTransaction((connection) -> {
				String player1Nick = player1 != null ? player1.getNick() : "Computer";
				player1Nick = URLUtil.urlEncode(player1Nick);

				String player2Nick = player2 != null ? player2.getNick() : "Computer";
				player2Nick = URLUtil.urlEncode(player2Nick);

				connection.insert(DBV5Consts.TABLE_GAME_LOG,
						new Object[] { null, getGameName(), variant, lobby.getID(), rated, player1 != null ? player1.getGZID() : "COMPUTER", player1Nick, player1 != null ? player1.getCompID() : null,
								player1 != null ? player1.getIP().getHostAddress() : null, oldRating1, oldRating12, player1 != null ? player1.getRating(variant) : 0,
								player1 != null ? player1.getRating2(variant) : 0, player1 != null ? player1.getAvatar() : 0, player2 != null ? player2.getGZID() : "COMPUTER", player2Nick,
								player2 != null ? player2.getCompID() : null, player2 != null ? player2.getIP().getHostAddress() : null, oldRating2, oldRating22,
								player2 != null ? player2.getRating(variant) : 0, player2 != null ? player2.getRating2(variant) : 0, player2 != null ? player2.getAvatar() : 0,
								new Timestamp(startTime), System.currentTimeMillis() - startTime, turns, winner, null });

				return null;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
	}

	protected void logIP(Session session, String ip) {
		try {
			executeTransaction((connection) -> {
				logIP(connection, session, ip);
				return null;
			});
		} catch (SQLException e) {
			handleException(session, e);
		} catch (InterruptedException e) {
		}
	}

	protected void logIP(SQLConnection connection, Session session, String ip) throws SQLException {
		logIP(connection, session.getGZID(), ip);
	}

	protected void logIP(SQLConnection connection, String gzid, String ip) throws SQLException {
		if (!connection.execute(SQLCommand.insert(DBV5Consts.TABLE_IPS, true, new Object[] { ip, 1, gzid, new Timestamp(System.currentTimeMillis()), false, null, null, null })))
			connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_IPS + " SET times_used=times_used+1, last_time_used_by=" + SQLFormater.formatValue(gzid) + ", last_time_used_when="
					+ SQLFormater.formatValue(new Timestamp(System.currentTimeMillis())) + " WHERE ip=" + SQLFormater.formatValue(ip) + " AND last_time_used_by<>" + SQLFormater.formatValue(gzid));
	}

	protected void logNick(Session session, String nick) {
		try {
			executeTransaction((connection) -> {
				logNick(connection, session, nick);
				return null;
			});
		} catch (SQLException e) {
			handleException(session, e);
		} catch (InterruptedException e) {
		}
	}

	protected void logNick(SQLConnection connection, Session session, String nick) throws SQLException {
		logNick(connection, session.getGZID(), nick);
	}

	protected void logNick(SQLConnection connection, String gzid, String nick) throws SQLException {
		nick = URLUtil.urlEncode(nick);

		if (!connection.execute(SQLCommand.insert(DBV5Consts.TABLE_NICK_LOG, true, new Object[] { nick, gzid, 1, new Timestamp(System.currentTimeMillis()) })))
			connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_NICK_LOG + " SET times_used = times_used + 1, last_time_used_when = NOW() WHERE nick = " + SQLFormater.formatValue(nick)
					+ " AND gzid = " + SQLFormater.formatValue(gzid));
	}

	protected void logPassword(Session session, String password) {
		try {
			executeTransaction((connection) -> {
				logPassword(connection, session, password);
				return null;
			});
		} catch (SQLException e) {
			handleException(session, e);
		} catch (InterruptedException e) {
		}
	}

	protected void logPassword(SQLConnection connection, Session session, String password) throws SQLException {
		logPassword(connection, session.getGZID(), password);
	}

	protected void logPassword(SQLConnection connection, String gzid, String password) throws SQLException {
		password = URLUtil.urlEncode(password);

		if (!connection.execute(SQLCommand.insert(DBV5Consts.TABLE_PASSWORDS, true, new Object[] { password, 1, gzid, new Timestamp(System.currentTimeMillis()) })))
			connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_PASSWORDS + " SET times_used=times_used+1, last_time_used_by=" + SQLFormater.formatValue(gzid) + ", last_time_used_when="
					+ SQLFormater.formatValue(new Timestamp(System.currentTimeMillis())) + " WHERE password=" + SQLFormater.formatValue(password) + " AND last_time_used_by<>"
					+ SQLFormater.formatValue(gzid));
	}

	public void logToErr(String message) {
		if (log != null)
			log.logToErr(message);
		else
			Log.logToErr(System.err, null, message, null);
	}

	public void logToErr(String prefix, String message) {
		if (log != null)
			log.logToErr(prefix, message);
		else
			Log.logToErr(System.err, prefix, message, null);
	}

	public void logToErr(String prefix, String message, Throwable e) {
		if (log != null)
			log.logToErr(prefix, message, e);
		else
			Log.logToErr(System.err, prefix, message, e);
	}

	public void logToErr(String prefix, String[] messages) {
		if (log != null)
			log.logToErr(prefix, messages);
		else
			Log.logToErr(System.err, prefix, messages, null);
	}

	public void logToErr(String prefix, String[] messages, Throwable e) {
		if (log != null)
			log.logToErr(prefix, messages, e);
		else
			Log.logToErr(System.err, prefix, messages, e);
	}

	public void logToErr(String message, Throwable e) {
		if (log != null)
			log.logToErr(message, e);
		else
			Log.logToErr(System.err, null, message, e);
	}

	public void logToErr(Throwable e) {
		if (log != null)
			log.logToErr(e);
		else
			Log.logToErr(System.err, null, (String) null, e);
	}

	public void logToOut() {
		logToOut("");
	}

	public void logToOut(String message) {
		logToOut(null, message);
	}

	public void logToOut(String prefix, String message) {
		if (log != null)
			log.logToOut(prefix, message);
		else
			Log.logToOut(System.out, prefix, message);
	}

	public void logToOut(String prefix, String[] messages) {
		if (log != null)
			log.logToOut(prefix, messages);
		else
			Log.logToOut(System.out, prefix, messages);
	}

	public void logToOut(String[] messages) {
		if (log != null)
			log.logToOut(messages);
		else
			Log.logToOut(System.out, null, messages);
	}

	private String makeUsersJoinByEmail(String email) {
		return "SELECT * FROM " + DBV5Consts.TABLE_USERS + " LEFT JOIN " + DBV5Consts.TABLE_LOGIN_LOG + " ON " + DBV5Consts.TABLE_USERS + ".last_login=" + DBV5Consts.TABLE_LOGIN_LOG + ".id" + " LEFT JOIN "
				+ DBV5Consts.TABLE_ACCESS_LOG + " ON " + DBV5Consts.TABLE_USERS + ".last_access=" + DBV5Consts.TABLE_ACCESS_LOG + ".id" + " WHERE " + DBV5Consts.TABLE_USERS + ".email="
				+ SQLFormater.formatValue(email);
	}

	private String makeUsersJoinByGZID(String gzid) {
		return "SELECT * FROM " + DBV5Consts.TABLE_USERS + " LEFT JOIN " + DBV5Consts.TABLE_LOGIN_LOG + " ON " + DBV5Consts.TABLE_USERS + ".last_login=" + DBV5Consts.TABLE_LOGIN_LOG + ".id" + " LEFT JOIN "
				+ DBV5Consts.TABLE_ACCESS_LOG + " ON " + DBV5Consts.TABLE_USERS + ".last_access=" + DBV5Consts.TABLE_ACCESS_LOG + ".id" + " WHERE " + DBV5Consts.TABLE_USERS + ".gzid="
				+ SQLFormater.formatValue(gzid);
	}

	public boolean mute(Connection user, long time, Set<BanType> type, String reason) {
		return addBan(user, time, type, BanRestriction.CHAT, reason);
	}

	public boolean mute(Connection user, long time, Set<BanType> type, String admin, String reason) {
		return addBan(user, time, type, BanRestriction.CHAT, admin, reason);
	}

	public boolean mute(Connection user, long time, String reason) {
		return mute(user, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), reason);
	}

	public boolean mute(Connection user, long time, String admin, String reason) {
		return mute(user, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean mute(Connection user, Set<BanType> type, String reason) {
		return addBan(user, type, BanRestriction.CHAT, reason);
	}

	public boolean mute(Connection user, String reason) {
		return mute(user, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), reason);
	}

	public boolean mute(int room, Connection user, long time, Set<BanType> type, String reason) {
		return addBan(room, user, time, type, BanRestriction.CHAT, reason);
	}

	public boolean mute(int room, Connection user, long time, Set<BanType> type, String admin, String reason) {
		return addBan(room, user, time, type, BanRestriction.CHAT, admin, reason);
	}

	public boolean mute(int room, String gzid, long time, Set<BanType> type, String admin, String reason) {
		return addBan(room, gzid, time, type, BanRestriction.CHAT, admin, reason);
	}

	public boolean mute(int room, Connection user, long time, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), reason);
	}

	public boolean mute(int room, Connection user, long time, String admin, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean mute(String gzid, long time, EnumSet<BanType> type, String admin, String reason) {
		return addBan(gzid, time, type, BanRestriction.CHAT, admin, reason);
	}

	public boolean mute(String gzid, long time, String admin, String reason) {
		return mute(gzid, time, EnumSet.of(BanType.GZID, BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean muteByCompid(Connection user, long time, String reason) {
		return mute(user, time, EnumSet.of(BanType.COMPID), reason);
	}

	public boolean muteByCompid(Connection user, long time, String admin, String reason) {
		return mute(user, time, EnumSet.of(BanType.COMPID), admin, reason);
	}

	public boolean muteByCompid(Connection user, String reason) {
		return mute(user, EnumSet.of(BanType.COMPID), reason);
	}

	public boolean muteByCompid(int room, Connection user, long time, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.COMPID), reason);
	}

	public boolean muteByCompid(int room, Connection user, long time, String admin, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.COMPID), admin, reason);
	}

	public boolean muteByCompidAndIP(Connection user, long time, String reason) {
		return mute(user, time, EnumSet.of(BanType.COMPID, BanType.IP), reason);
	}

	public boolean muteByCompidAndIP(Connection user, long time, String admin, String reason) {
		return mute(user, time, EnumSet.of(BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean muteByCompidAndIP(Connection user, String reason) {
		return mute(user, EnumSet.of(BanType.COMPID, BanType.IP), reason);
	}

	public boolean muteByCompidAndIP(int room, Connection user, long time, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.COMPID, BanType.IP), reason);
	}

	public boolean muteByCompidAndIP(int room, Connection user, long time, String admin, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.COMPID, BanType.IP), admin, reason);
	}

	public boolean muteByGZID(Connection user, long time, String reason) {
		return mute(user, time, EnumSet.of(BanType.GZID), reason);
	}

	public boolean muteByGZID(Connection user, long time, String admin, String reason) {
		return mute(user, time, EnumSet.of(BanType.GZID), admin, reason);
	}

	public boolean muteByGZID(Connection user, String reason) {
		return mute(user, EnumSet.of(BanType.GZID), reason);
	}

	public boolean muteByGZID(int room, Connection user, long time, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.GZID), reason);
	}

	public boolean muteByGZID(int room, Connection user, long time, String admin, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.GZID), admin, reason);
	}

	public boolean muteByGZIDAndCompid(Connection user, long time, String reason) {
		return mute(user, time, EnumSet.of(BanType.GZID, BanType.COMPID), reason);
	}

	public boolean muteByGZIDAndCompid(Connection user, long time, String admin, String reason) {
		return mute(user, time, EnumSet.of(BanType.GZID, BanType.COMPID), admin, reason);
	}

	public boolean muteByGZIDAndCompid(Connection user, String reason) {
		return mute(user, EnumSet.of(BanType.GZID, BanType.COMPID), reason);
	}

	public boolean muteByGZIDAndCompid(int room, Connection user, long time, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.GZID, BanType.COMPID), reason);
	}

	public boolean muteByGZIDAndCompid(int room, Connection user, long time, String admin, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.GZID, BanType.COMPID), admin, reason);
	}

	public boolean muteByGZIDAndIP(Connection user, long time, String reason) {
		return mute(user, time, EnumSet.of(BanType.GZID, BanType.IP), reason);
	}

	public boolean muteByGZIDAndIP(Connection user, long time, String admin, String reason) {
		return mute(user, time, EnumSet.of(BanType.GZID, BanType.IP), admin, reason);
	}

	public boolean muteByGZIDAndIP(Connection user, String reason) {
		return mute(user, EnumSet.of(BanType.GZID, BanType.IP), reason);
	}

	public boolean muteByGZIDAndIP(int room, Connection user, long time, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.GZID, BanType.IP), reason);
	}

	public boolean muteByGZIDAndIP(int room, Connection user, long time, String admin, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.GZID, BanType.IP), admin, reason);
	}

	public boolean muteByIP(Connection user, long time, String reason) {
		return mute(user, time, EnumSet.of(BanType.IP), reason);
	}

	public boolean muteByIP(Connection user, long time, String admin, String reason) {
		return mute(user, time, EnumSet.of(BanType.IP), admin, reason);
	}

	public boolean muteByIP(Connection user, String reason) {
		return mute(user, EnumSet.of(BanType.IP), reason);
	}

	public boolean muteByIP(int room, Connection user, long time, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.IP), reason);
	}

	public boolean muteByIP(int room, Connection user, long time, String admin, String reason) {
		return mute(room, user, time, EnumSet.of(BanType.IP), admin, reason);
	}

	private void notifyCounts(GZStruct response) {
		int playing = 0;
		for (Lobby lobby : lobbies) {
			if (!lobby.isOpen())
				continue;

			playing += lobby.playingCount();
		}

		int playedCount = 0;
		try {
			playedCount = executeTransaction((connection) -> {
				int result = 0;
				try (ResultSet rs = connection.executeQuery("SELECT count(*) FROM " + DBV5Consts.TABLE_GAME_LOG)) {
					if (rs.next())
						result = rs.getInt(1);
				}

				return result;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		response.setString("cstat", getRegisteredUsers() + ":" + playedCount + ":" + getOnlineUsers() + ":" + playing);
	}

	private Config parseConfig(File configFile) throws ParserConfigurationException, SAXException, IOException {
		if (configFile == null)
			return null;

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(false);
		DocumentBuilder docBuilder = dbf.newDocumentBuilder();
		Document doc = docBuilder.parse(configFile);
		Tree.Node<ConfigEntry> root = new Tree.Node<>();

		NodeList childs = doc.getChildNodes();
		loadTree(childs, root);

		return new Config(root);
	}

	@SuppressWarnings("unchecked")
	private void parseServerConfigs() {
		ArrayList<ConfigEntry> globalConfig = new ArrayList<>();

		Tree.Node<ConfigEntry> root = config.getChild(0);
		ConfigEntry entry = root.getValue();
		if (!entry.getName().equalsIgnoreCase("config"))
			return;

		for (int j = 0; j < root.getChildCount(); j++) {
			Tree.Node<ConfigEntry> node = root.getChild(j);
			entry = node.getValue();
			if (!entry.getName().equalsIgnoreCase("servers"))
				globalConfig.add(entry);
			else
				for (int k = 0; k < node.getChildCount(); k++) {
					Tree.Node<ConfigEntry> child = node.getChild(k);
					entry = child.getValue();
					if (entry.getName().equalsIgnoreCase("server") && entry instanceof MapConfigEntry) {
						Map<String, String> attrs = ((MapConfigEntry) entry).map();
						try {
							String name = attrs.get("name");
							if (!serverClasses.containsKey(name))
								serverClasses.put(name, (Class<? extends Container>) Class.forName(attrs.get("class")));
						} catch (ClassNotFoundException e) {
							handleException(e);
						}
					}
				}
		}

		config = new Config(globalConfig);
	}

	public void post(NonReturnableProcessWithoutArg process) {
		queue.post(process, (e) -> logToErr(e));
	}

	public void postAndWait(NonReturnableProcessWithoutArg process) throws InterruptedException {
		try {
			queue.postAndWait(process);
		} catch (RuntimeException e) {
			logToErr(e);
		}
	}

	public List<Lobby> lobbies() {
		synchronized (lobbies) {
			return new ArrayList<>(lobbies);
		}
	}

	public void send(NonReturnableProcessWithoutArg process) {
		try {
			queue.send(process);
		} catch (InterruptedException e) {
		} catch (RuntimeException e) {
			handleException(e);
		}
	}

	public <T> T send(ReturnableProcess<T> process) {
		try {
			return queue.send(process);
		} catch (InterruptedException e) {
		} catch (RuntimeException e) {
			handleException(e);
		}

		return null;
	}

	@SuppressWarnings("unused")
	@Override
	protected void service(HttpServletRequest req0, HttpServletResponse resp0) throws ServletException, IOException {
		IPSession ipSession;
		synchronized (ips) {
			String ip = req0.getRemoteAddr();
			ipSession = ips.get(ip);
			if (ipSession == null) {
				ipSession = new IPSession(ip);
				ips.put(ip, ipSession);
			}
		}

		if (ipSession.isBlocked())
			return;

		if (!ipSession.checkSpam())
			return;

		if (DEBUG_SERVICE && DebugUtil.DEBUG_MODE) {
			ArrayList<String> messages = new ArrayList<>();
			messages.add("---------------------------------------------------------------------------");

			String alohay = "";
			Enumeration<String> headers = req0.getHeaderNames();
			while (headers.hasMoreElements()) {
				String header = headers.nextElement();
				String value = req0.getHeader(header);

				if (header.equalsIgnoreCase("ALOHAY"))
					alohay = value;

				messages.add(header + "=" + value);
			}

			messages.add("");

			logToOut(messages.toArray(new String[] {}));
		}

		String alohay = req0.getHeader("ALOHAY");
		if (alohay == null)
			return;

		resp0.setHeader("X-Permitted-Cross-Domain-Policies", "all");

		Session session = null;
		try {
			ServletRequest req = req0;
			ServletResponse resp = resp0;
			resp.setContentType("text/plain");

			BufferedReader reader = new BufferedReader(new InputStreamReader(req.getInputStream()));

			String content = "";
			while (true) {
				String line = reader.readLine();
				if (line == null)
					break;

				content += line;
			}

			if (!content.startsWith("t="))
				return;

			content = GZUtils.unescape(content.substring(2));
			if (content == null)
				return;

			GZStruct request = GZUtils.unpack_packet(content);
			if (request == null)
				return;

			if (DEBUG_SERVICE && DebugUtil.DEBUG_MODE)
				logToOut(new String[] { "Request:", request.toString(), "" });

			String cm = request.getString("cm");
			if (cm == null)
				return;

			String fsid = request.getString("s");
			String CRK;
			if (fsid != null) {
				CRK = GZUtils.hmx(fsid).substring(0, 20);
				session = getSession(fsid);
				if (session != null) {
					try {
						Session s = session;
						executeTransaction((connection) -> {
							s.created = new Timestamp(System.currentTimeMillis());
							connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_SESSIONS + " SET created=" + SQLFormater.formatValue(s.getCreated()) + " WHERE fsid=" + SQLFormater.formatValue(fsid));

							return null;
						});
					} catch (SQLException e) {
						handleException(e);
					} catch (InterruptedException e) {
					}

					session.ipSession = ipSession;
				}
			} else
				CRK = "";

			if (!GZUtils.hmx(content + CRK).substring(0, 8).equals(alohay))
				return;

			String compid = request.getString("i");

			PrintWriter out = resp.getWriter();
			GZStruct response = new GZStruct();

			try {
				switch (cm) {
					case "in": {
						String version = request.getString("v");

						if (session != null)
							response.setString("com", "logged");
						else
							response.setString("com", "in");

						break;
					}

					case "up": {
						String vd = request.getString("vd");

						if (session == null)
							response.setString("com", "logout");

						break;
					}

					case "preg": {
						if (session != null) {
							response.setString("alert", "You must be not logged.");
							break;
						}

						response.setString("com", "ok");
						Captcha captcha = buildCaptcha();
						String vn = captcha.getVN();
						String vs = captcha.getVS();
						response.setString("vn", vn);
						response.setString("vs", vs);
						break;
					}

					case "reg": {
						if (session != null) {
							response.setString("alert", "You must be not logged.");
							break;
						}

						String email = request.getString("e").trim().toLowerCase();
						String password = request.getString("p");
						Gender gender = Gender.values()[request.getInt("gender")];
						int bday = request.getInt("bday");
						int bmonth = request.getInt("bmonth");
						int byear = request.getInt("byear");
						String lang = request.getString("lang");
						String country = request.getString("c");
						String nick = filterMessage(request.getString("nick"));
						int question = request.getInt("qst");
						String answer = request.getString("ans");
						int avatar = request.getInt("a");
						boolean showGZID = request.getBoolean("allowe");
						boolean showGenderAge = request.getBoolean("allowp");
						boolean showCountry = request.getBoolean("allowc");
						String vn = request.getString("vn");
						String vt = request.getString("vt");

						Captcha captcha = getCaptcha(vn);
						if (captcha == null || captcha.isExpired()) {
							captcha = buildCaptcha();
							response.setString("vn", captcha.getVN());
							response.setString("vs", captcha.getVS());
							response.setString("alert", "Captcha expired. Please try again.");
							break;
						}

						if (!captcha.getVT().equalsIgnoreCase(vt)) {
							response.setString("vn", captcha.getVN());
							response.setString("vs", captcha.getVS());
							response.setString("alert", "Captcha text doesnt match with image.");
							break;
						}

						User user = getUserByEmail(email);
						if (user != null) {
							response.setString("vn", captcha.getVN());
							response.setString("vs", captcha.getVS());
							response.setString("alert", "E-mail " + email + " already registered.");
							break;
						}

						if (nick.equals(""))
							nick = "Player";

						try {
							String gzid = DigestUtil.md5(Long.toString(System.currentTimeMillis()));
							String compID = compid;
							String ip = ipSession.getIP();
							String n = nick;
							int result = executeTransaction((connection) -> {
								int count = getRegisteredAccountsCount(compID, ip, DEFAULT_REGISTERED_ACCOUNT_CHECK_PERIOD_SEC);
								if (count > MAX_ACCOUNTS_REGISTERS_PER_DAY)
									return 1;

								connection.insert(DBV5Consts.TABLE_USERS,
										new Object[] { gzid, email, URLUtil.urlEncode(password), null, null, false, null, null, false, null, 0, null, false, true, avatar, lang, country, bday, bmonth,
												byear, showGZID, showGenderAge, showCountry, gender.getValue(), question, URLUtil.urlEncode(answer), URLUtil.urlEncode(n), 0, 0 });

								connection.insert(DBV5Consts.TABLE_REGISTERS, true, new Object[] { gzid, compID, ip, null });
								logEmail(connection, gzid, email);
								logPassword(connection, gzid, password);
								logNick(connection, gzid, n);

								return 0;
							});

							if (result == 1) {
								response.setString("vn", captcha.getVN());
								response.setString("vs", captcha.getVS());
								response.setString("alert", "You reached the maximum number of registered accounts per day.");
								break;
							}

							response.setString("com", "registered");
							logToOut("REG",
									"[" + ipSession.getIP() + "] " + "gzid=" + gzid + // gzid
											" email=" + email + // email
											" password=" + password + // password
											" avatar=" + avatar + // avatar
											" lang=" + lang + // language
											" country=" + country + // country
											" birthdate=" + String.format("%02d", bday) + "/" + String.format("%02d", bmonth) + "/" + String.format("%04d", byear) + // birthdate
											" gender=" + gender + " question=" + question + " answer=" + answer + " nick=" + nick);

							break;
						} catch (SQLException e) {
							handleException(e);
						} catch (InterruptedException e) {
						}

						response.setString("vn", captcha.getVN());
						response.setString("vs", captcha.getVS());
						response.setString("alert", "Internal error on registering. Please try again later.");

						break;
					}

					case "search": {
						String search = request.getString("e");
						User user;
						if (search.indexOf("@") > 0)
							user = getUserByEmail(search);
						else
							user = getUserByGZID(search);

						if (user == null) {
							response.setString("com", "not");
							break;
						}

						Lobby lobby = findLobbyContainingUser(user.getGZID());
						if (lobby == null) {
							Session s = getSessionByGZID(user.getGZID());
							if (s == null)
								response.setString("com", "not");
							else
								response.setString("com", "ok");

							break;
						}

						response.setString("com", "ok");
						response.setString("res", lobby.getContainer().getGameName() + "|" + completeLobbyName(lobby) + "|" + user.getNick());

						break;
					}

					case "login": {
						if (session != null)
							response.setString("alert", "You are already logged.");
						else {
							String email = request.getString("e").trim().toLowerCase();

							LoginIPPair lip = new LoginIPPair(email, ipSession.getIP());
							LoginAttemp attemp = loginAttempts.get(lip);
							if (attemp != null && attemp.isBlocked()) {
								response.setString("com", "nopass");
								break;
							}

							String password = request.getString("p");

							if (compid == null)
								compid = GZUtils.getID(email + Long.toString(System.currentTimeMillis()));

							session = auth(email, password, compid, ipSession.getIP());
							if (session == null) {
								if (attemp == null) {
									attemp = new LoginAttemp(lip);
									loginAttempts.put(lip, attemp);
								}

								attemp.increment();

								response.setString("com", "nopass");
							} else {
								loginAttempts.remove(lip);

								session.ipSession = ipSession;

								response.setString("com", "logged");

								logToOut("LOGIN", "[" + ipSession.getIP() + "] " + "session={" + session + "}");
							}
						}

						break;
					}

					case "remind": {
						String email = request.getString("e");
						int question = request.getInt("q");
						String answer = request.getString("a");

						User user = getUserByEmail(email);
						if (user == null) {
							response.setString("com", "inv");
							break;
						}

						if (!user.isActive()) {
							response.setString("com", "inv");
							break;
						}

						if (user.getQuestion() != question) {
							response.setString("com", "inv");
							break;
						}

						if (!answer.equalsIgnoreCase(user.getAnswer())) {
							response.setString("com", "inv");
							break;
						}

						SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");
						dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));

						// Local time zone
						SimpleDateFormat dateFormatLocal = new SimpleDateFormat("yyyy-MMM-dd HH:mm:ss");

						// Time in GMT
						Date gmtTime = dateFormatLocal.parse(dateFormatGmt.format(new Date()));

						MailService mailService = getMailService("no_reply");

						String message = "Hello,<br>";
						message += "As requested, here is your GameZ account password:<br>";
						message += user.getPassword() + "<br>";
						message += "<br>";
						message += "Request received from IP: " + ipSession.getIP() + " at " + gmtTime + " GMT<br>";
						message += "<br>";
						message += "Regards<br>";
						message += "<br>";
						message += "GameZ Team<br>";
						message += "<br>";
						message += "<a href=\"http://www.gamez.net.br\" target=\"_blank\">www.gamez.net.br</a>";

						MailUtil.sendMail(mailService.getHost(), mailService.getPort(), mailService.useAuth(), mailService.useSSL(), mailService.getSender(), mailService.getPassword(),
								user.getEmail(), "Password recover for " + user.getNick() + "", message, "UTF-8", "text/html");

						response.setString("com", "sent");

						logToOut("REMIND", "[" + ipSession.getIP() + "] session={" + session + "} email=" + email);

						break;
					}

					case "pf": {
						if (session == null) {
							response.setString("alert", "You must be logged.");
							break;
						}

						String vd = request.getString("vd");

						String email = request.getString("e").trim().toLowerCase();
						Gender gender = Gender.values()[request.getInt("gender")];
						int bday = request.getInt("bday");
						int bmonth = request.getInt("bmonth");
						int byear = request.getInt("byear");
						String lang = request.getString("lang");
						String country = request.getString("c");
						String nick = filterMessage(request.getString("nick"));
						int avatar = request.getInt("a");
						boolean showGZID = request.getBoolean("allowe");
						boolean showGenderAge = request.getBoolean("allowp");
						boolean showCountry = request.getBoolean("allowc");

						User user = session.getUser();
						user.email = email;
						user.gender = gender;
						user.bday = bday;
						user.bmonth = bmonth;
						user.byear = byear;
						user.lang = lang;
						user.country = country;

						if (nick.equals(""))
							nick = "Player";

						boolean sameNick = nick.equals(user.nick);

						user.nick = nick;
						user.avatar = avatar;
						user.allowe = showGZID;
						user.allowp = showGenderAge;
						user.allowc = showCountry;

						try {
							String gzid = session.getGZID();
							Session sess = session;
							String ip = ipSession.getIP();
							String n = nick;
							executeTransaction((connection) -> {
								connection.update(DBV5Consts.TABLE_USERS, new String[] { "gzid" }, new Object[] { gzid },
										new String[] { "email", "avatar", "lang", "country", "bday", "bmonth", "byear", "allowe", "allowp", "allowc", "gender", "nick" },
										new Object[] { email, avatar, lang, country, bday, bmonth, byear, showGZID, showGenderAge, showCountry, gender.getValue(), URLUtil.urlEncode(n) });

								if (!sameNick)
									logNick(connection, gzid, n);

								logToOut("PROFILE CHANGE", "[" + ip + "] " + "session={" + sess + "}" + // session
								" avatar=" + avatar + // avatar
								" lang=" + lang + // language
								" country=" + country + // country
								" birthdate=" + String.format("%02d", bday) + "/" + String.format("%02d", bmonth) + "/" + String.format("%04d", byear) + // birthdate
								" gender=" + gender + // gender
								" nick=" + n // nick
								);

								return null;
							});

							break;
						} catch (SQLException e) {
							handleException(e);
						} catch (InterruptedException e) {
						}

						response.setString("alert", "Internal error on updating your profile. Please try again later.");

						break;
					}

					case "chpass": {
						if (session == null) {
							response.setString("alert", "You must be logged.");
							break;
						}

						String oldPassword = request.getString("cr");
						String newPassword = request.getString("nw");

						if (!oldPassword.equals(session.getUser().getPassword()))
							response.setString("com", "nopass");
						else {
							try {
								String gzid = session.getGZID();
								executeTransaction((connection) -> {
									connection.update(DBV5Consts.TABLE_USERS, new String[] { "gzid" }, new Object[] { gzid }, new String[] { "password" },
											new Object[] { URLUtil.urlEncode(newPassword) });

									if (!oldPassword.equals(newPassword))
										logPassword(connection, gzid, newPassword);

									return null;
								});

								response.setString("com", "ok");
								logToOut("PASSWORD CHANGE",
										"[" + ipSession.getIP() + "] " + "session={" + session + "}" + // session
												" oldPassword=" + oldPassword + // old
																				// password
												" newPassword=" + newPassword // new
																				// password
								);

								break;
							} catch (SQLException e) {
								handleException(e);
							} catch (InterruptedException e) {
							}

							response.setString("alert", "Internal error on updating your password. Please try again later.");
						}

						break;
					}

					case "delacc": {
						if (session == null) {
							response.setString("alert", "You must be logged.");
							break;
						}

						String password = request.getString("p");
						User user = session.getUser();
						if (!user.getPassword().equals(password)) {
							response.setString("com", "nopass");
							break;
						}

						try {
							executeTransaction((connection) -> connection.update(DBV5Consts.TABLE_USERS, new String[] { "gzid" }, new Object[] { user.getGZID() }, new String[] { "active" },
									new Object[] { false }));
						} catch (SQLException e) {
							handleException(e);
						} catch (InterruptedException e) {
						}

						session.logout();
						session = null;

						response.setString("com", "ok");

						logToOut("DELACC", "[" + ipSession.getIP() + "] session={" + session + "}");

						break;
					}

					case "logout": {
						if (session != null) {
							logToOut("LOGOUT", "[" + ipSession.getIP() + "] " + "session={" + session + "}");

							session.logout();
							session = null;
						}

						response.setString("com", "logout");

						break;
					}

					case "zgrm": { // ação de grupo
						if (session == null) {
							response.setString("alert", "You must be logged.");
							break;
						}

						String zm = request.getString("zm");
						switch (zm) {
							case "create": {
								Group group = getGroupFromOwner(session.getGZID());
								if (group != null)
									break;

								String grname = request.getString("grname").trim();
								if (grname.equals("")) {
									response.setString("com", "noname");
									break;
								}

								String grpass = request.getString("grpass");
								if (grpass.length() < 4 || grpass.length() > 12) {
									response.setString("com", "nopass");
									break;
								}

								if (containsBadWord(grpass)) {
									response.setString("com", "nopass_chars");
									break;
								}

								Session s = session;
								group = executeTransaction((connection) -> createGroup(connection, s, grname, grpass));

								session.getUser().group = group;

								response.setString("com", "ok");

								logToOut("GROUP", "[" + ipSession.getIP() + "][CREATE] session={" + session + "} groupID=" + group.getID());

								break;
							}

							case "join": {
								String gruid = request.getString("gruid");
								String zjpass = request.getString("zjpass");

								Group group = getGroupFromID(gruid);
								if (group == null) {
									response.setString("com", "notfound");
									break;
								}

								if (!zjpass.equals(group.getPassword())) {
									response.setString("com", "nopass");
									break;
								}

								Session s = session;
								executeTransaction((connection) -> {
									joinGroup(connection, s, gruid);
									return null;
								});

								group.memberCount++;
								session.getUser().group = group;

								response.setString("com", "ok");

								logToOut("GROUP", "[" + ipSession.getIP() + "][JOIN] session={" + session + "} groupID=" + group.getID());

								break;
							}

							case "quitzgr": {
								Session s = session;
								String ip = ipSession.getIP();
								int result = executeTransaction((connection) -> {
									Group group = getGroupContainingUser(s.getGZID());
									if (group == null)
										return 1;

									group.memberCount--;
									s.getUser().group = null;

									leaveGroup(connection, s, group.getID());

									logToOut("GROUP", "[" + ip + "][LEAVE] session={" + s + "} groupID=" + group.getID());

									return 0;
								});

								if (result == 1) {
									response.setString("com", "notfound");
									break;
								}

								response.setString("com", "ok");

								break;
							}

							case "delzgroup": {
								String ps = request.getString("ps");

								Session s = session;
								String ip = ipSession.getIP();
								int result = executeTransaction((connection) -> {
									Group group = getGroupFromOwner(s.getGZID());
									if (group == null)
										return 1;

									if (!group.getPassword().equals(ps))
										return 2;

									s.getUser().group = null;

									deleteGroup(connection, group.getID());

									logToOut("GROUP", "[" + ip + "][REMOVE] session={" + s + "} groupID=" + group.getID());

									return 0;
								});

								if (result == 1) {
									response.setString("com", "notfound");
									break;
								} else if (result == 2) {
									response.setString("com", "nopass");
									break;
								}

								response.setString("com", "ok");

								break;
							}

							case "modify": {
								String grname = request.getString("grname").trim();
								String grpass = request.getString("grpass");

								if (grname != null && grname.equals("")) {
									response.setString("com", "noname");
									break;
								}

								if (grpass != null && (grpass.length() < 4 || grpass.length() > 12)) {
									response.setString("com", "nopass");
									break;
								}

								Session s = session;
								String ip = ipSession.getIP();
								int result = executeTransaction((connection) -> {
									Group group = getGroupFromOwner(s.getGZID());
									if (group == null)
										return 1;

									updateGroup(connection, group.getID(), grname, grpass);

									logToOut("GROUP", "[" + ip + "][MODIFY] session={" + s + "} groupID=" + group.getID() + (grname != null ? " name=" + grname : "")
											+ (grpass != null ? " password=" + grpass : ""));

									return 0;
								});

								if (result == 1) {
									response.setString("com", "notfound");
									break;
								}

								response.setString("com", "ok");

								break;
							}

							case "kickmember": {
								String kuid = request.getString("kuid");

								Session s = session;
								String ip = ipSession.getIP();
								int result = executeTransaction((connection) -> {
									Group group = getGroupFromOwner(s.getGZID());
									if (group == null)
										return 1;

									group.memberCount--;
									leaveGroup(connection, kuid, group.getID());

									logToOut("GROUP", "[" + ip + "][KICK] session={" + s + "} groupID=" + group.getID() + " kicked=" + kuid);

									return 0;
								});

								if (result == 1) {
									response.setString("com", "notfound");
									break;
								}

								response.setString("com", "ok");
								break;
							}
						}

						break;
					}

					default: {
						logToErr("Invalid request " + cm + (session != null ? " sent from user " + session.getUser() + " with ip " + ipSession.getIP() : " from remote ip " + ipSession.getIP()));
						response.setString("alert", "Function Unavailable.");
					}
				}
			} catch (Throwable e) {
				handleException(e);
				response.setString("alert", "Internal Error!");
			}

			String com = response.getString("com");
			if (session != null && (com == null || !com.equals("logout") && !com.equals("off")))
				updateSession(session, response);
			else
				notifyCounts(response);

			if (DEBUG_SERVICE && DebugUtil.DEBUG_MODE)
				logToOut(new String[] { "Response:", response.toString(), "" });

			if (response.size() > 0) {
				String packed = GZUtils.pack_packet(response);
				out.print("t=" + GZUtils.escape(packed));
				out.flush();
			}
		} catch (Exception e) {
			handleException(session, e);
		}
	}

	public boolean containsBadWord(String message) {
		if (message.contains("_|_") || message.contains("_!_") || message.contains("_I_") || message.contains("==D") || message.contains("_)_)") || message.contains("(_(_"))
			return true;

		String[] words = message.split("[[ ]*|[,]*|[\\.]*|[:]*|[/]*|[!]*|[?]*|[+]*]+");
		ArrayList<String> words2 = new ArrayList<>();
		String lastWord = "";
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			if (word.length() == 1)
				lastWord += word;
			else {
				if (lastWord.length() > 0) {
					words2.add(lastWord);
					lastWord = "";
				}

				words2.add(word);
			}
		}

		if (lastWord.length() > 0)
			words2.add(lastWord);

		words = words2.toArray(new String[] {});

		for (String word : words)
			if (isBadWord(word))
				return true;

		return false;
	}

	private static final String C2A0 = URLUtil.urlDecode("%C2%A0");

	public static String processHyperLinks(String text) {
		text = text.replaceAll(PatternUtil.WEB_URL.pattern(), "***");

		return text;
	}

	public String filterMessage(String message) {
		return filterMessage(message, true, null);
	}

	public String filterMessage(String message, Runnable onBadWord) {
		return filterMessage(message, true, onBadWord);
	}

	public String filterMessage(String message, boolean checkBadWords, Runnable onBadWord) {
		message = message.trim();
		if (message.equals(""))
			return "";

		message = message.replaceAll("[\\u0000-\\u0019]", "");
		message = message.replaceAll("[\\u2000-\\u200F]", "");
		message = message.replaceAll("[\\u06FF-\\u1E7F]", "");
		message = message.replaceAll("[\\u1EFA-\\uFB1C]", "");
		message = message.replaceAll("[\\uFEFD-\\uFFFE]", "");
		message = message.replace("█", "");
		message = message.replace("ͦ", "");
		message = message.replace("ͥ", "");
		message = message.replace(C2A0, "");

		String newValue;
		while (true) {
			newValue = message.replace("  ", " ");
			if (newValue.length() == message.length())
				break;

			message = newValue;
		}

		message = newValue;

		if (checkBadWords && containsBadWord(message)) {
			if (onBadWord != null)
				onBadWord.run();

			return "***";
		}

		return processHyperLinks(message);
	}

	public MailService getMailService(String id) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_MAIL_SERVICES, new String[] { "id" }, new Object[] { id })) {
					if (!rs.next())
						return null;

					return new MailService(rs);
				}
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public Lobby findLobbyContainingUser(String gzid) {
		ServletContext context = getServletContext();
		ArrayList<Container> games = (ArrayList<Container>) context.getAttribute("games");
		if (games == null)
			return null;

		for (Container game : games) {
			Vector<Lobby> lobbies = game.lobbies;
			for (Lobby lobby : lobbies) {
				if (!lobby.isOpen())
					continue;

				if (lobby.containsUser(gzid) || lobby.isPlaying(gzid))
					return lobby;
			}
		}

		return null;
	}

	private void leaveGroup(SQLConnection connection, String gzid, String groupID) throws SQLException {
		connection.delete(DBV5Consts.TABLE_GROUP_MEMBERS, new String[] { DBV5Consts.TABLE_GROUP_MEMBERS + ".group", "gzid" }, new Object[] { groupID, gzid });
		connection.update(DBV5Consts.TABLE_GROUPS, new String[] { "id" }, new Object[] { groupID }, new String[] { "member_count" }, new Object[] { new SQLExpression("member_count - 1") });
	}

	private void updateGroup(SQLConnection connection, String id, String name, String password) throws SQLException {
		connection.executeUpdate("UPDATE " + DBV5Consts.TABLE_GROUPS + " SET " + (name != null ? " name=" + SQLFormater.formatValue(name) : "")
				+ (password != null ? (name != null ? "," : "") + " password=" + SQLFormater.formatValue(password) : "") + " WHERE id=" + SQLFormater.formatValue(id));
	}

	private void deleteGroup(SQLConnection connection, String id) throws SQLException {
		connection.delete(DBV5Consts.TABLE_GROUPS, new String[] { "id" }, new Object[] { id });
	}

	public void leaveGroup(Session session, String groupID) {
		try {
			executeTransaction((connection) -> {
				leaveGroup(connection, session, groupID);
				return null;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
	}

	private void leaveGroup(SQLConnection connection, Session session, String groupID) throws SQLException {
		leaveGroup(connection, session.getGZID(), groupID);
	}

	public Group getGroupContainingUser(String gzid) {
		try {
			return executeTransaction((connection) -> getGroupContainingUser(connection, gzid));
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return null;
	}

	private Group getGroupContainingUser(SQLConnection connection, String gzid) throws SQLException {
		String groupID = null;
		try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_GROUP_MEMBERS, new String[] { "gzid" }, new Object[] { gzid })) {
			if (!rs.next())
				return null;

			groupID = rs.getString("group");
		}

		try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_GROUPS, new String[] { "id" }, new Object[] { groupID })) {
			if (rs.next()) {
				Group group = new Group(rs);
				return group;
			}
		}

		return null;
	}

	private void joinGroup(SQLConnection connection, Session session, String groupID) throws SQLException {
		String gzid = session.getGZID();
		connection.insert(DBV5Consts.TABLE_GROUP_MEMBERS, new Object[] { groupID, gzid, null });
		connection.update(DBV5Consts.TABLE_GROUPS, new String[] { "id" }, new Object[] { groupID }, new String[] { "member_count" }, new Object[] { new SQLExpression("member_count + 1") });
	}

	public int getRegisteredAccountsCount(String compid, String ip, int period) {
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection
						.executeQuery("SELECT count(*) FROM " + DBV5Consts.TABLE_REGISTERS + " WHERE (" + (compid != null ? "compid=" + SQLFormater.formatValue(compid) + " OR " : "") + "ip="
								+ SQLFormater.formatValue(ip) + ") AND NOW() <= DATE_ADD(registered_when, INTERVAL " + period + " SECOND)")) {
					if (rs.next())
						return rs.getInt(1);
				}

				return 0;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return 0;
	}

	private Group createGroup(SQLConnection connection, Session session, String grname, String grpass) throws SQLException {
		String gzid = session.getGZID();
		long time = System.currentTimeMillis();
		Timestamp creationTime = new Timestamp(time);
		String id = DigestUtil.md5(Long.toString(time));
		connection.insert(DBV5Consts.TABLE_GROUPS, new Object[] { id, grpass, grname, creationTime, gzid, 1, true });
		connection.insert(DBV5Consts.TABLE_GROUP_MEMBERS, new Object[] { id, gzid, null });
		Group group = new Group(id, grpass, grname, creationTime, gzid);
		return group;
	}

	public void updateRating(String variant, String user, int rating, int rating2) {
		try {
			executeTransaction((connection) -> {
				return connection.update(DBV5Consts.TABLE_STATS, new String[] { "game", "variant", "gzid" }, new Object[] { getGameName(), variant, user }, new String[] { "rating", "rating2" },
						new Object[] { rating, rating2 });
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}
	}

	@SuppressWarnings("unused")
	private void updateSession(Session session, GZStruct response) {
		User user = session.getUser();
		user.refresh();
		PremiumData premiumData = user.getPremiumData();
		if (premiumData != null)
			premiumData.refresh();

		String gzid = session.getGZID();
		int rating = user.getRating() / 1000;
		Group group = user.getGroup();
		boolean blocked = isMuted(-1, gzid, user.getCompID(), session.getIPSession() != null ? session.getIPSession().getIP() : null);
		response.setString("vd",
				"" + ":" + // 0
						"" + ":" + // 1
						session.getFSID() + ":" + // 2 - fsid
						gzid + ":" + // 3 - gzid
						user.getCountry() + ":" + // 4 - country
						user.getEmail() + ":" + // 5 - e-mail
						user.getGender().getValue() + ":" + // 6 - gender
						String.format("%04d", user.getBirthYear()) + String.format("%02d", user.getBirthMonth()) + String.format("%02d", user.getBirthDay()) + ":" + // 7
																																										// -
																																										// birthdate
						user.getAvatar() + ":" + // 8 - avatar
						(user.isShowingGZID() ? "1" : "0") + ":" + // 9 - show
																	// gzid
						(user.isShowingGender() ? "1" : "0") + ":" + // 10 -
																		// show
																		// gender
						(user.isShowingCountry() ? "1" : "0") + ":" + // 11 -
																		// show
																		// country
						Long.toString(user.getRegistered().getTime() / 1000) + ":" + // 12
																						// -
																						// regdate
						user.getLang() + ":" + // 13 - lang
						(blocked ? "1" : "0") + ":" + // 14 - blockedchat
						(group != null ? group.getID() + "_" + group.getOwner() : "") + ":" + // 15
																								// -
						// zgroup
						((premiumData != null && !premiumData.isExpired() || rating >= 100) ? "1" : "0") + ":" + // 16
						// -
						// premium
						user.getStars() + ":" + // 17 - stars
						rating + ":" + // 18 - rating
						user.getPlayeds() + ":" + // 19 - total games
						user.getWins() + ":" + // 20 - wins
						"0" + ":" + // 21 - tournaments
						"0" + ":" + // 22 - 1st place
						"0" + ":" + // 23 - 2nd place
						"0" + ":" + // 24 - 3rd place
						"0" + ":" + // 25 - refused
						user.getNick() + ":" + // 26 - nick
						(isAdmin(gzid) ? "1" : "0") // 27 - admin
		);

		GZStruct subdata = new GZStruct();
		subdata.setLong("l", user.getLastLogin().getTime() / 1000);
		response.setStruct("dt", subdata);

		ArrayList<String> ips = new ArrayList<>();
		GZStruct srv = new GZStruct();

		int online = getOnlineUsers();

		int playing = 0;
		for (Lobby lobby : lobbies) {
			if (!lobby.isOpen())
				continue;

			playing += lobby.playingCount();

			int id = lobby.getID();
			String lobbyHost = lobby.getLobbyHost();
			int lobbyPort = lobby.getLobbyPort();
			String gameHost = lobby.getGameHost();
			int gamePort = lobby.getGamePort();
			int users = lobby.userCount();

			int index1 = ips.indexOf(lobbyHost);
			if (index1 == -1) {
				index1 = ips.size();
				ips.add(lobbyHost);
			}

			int index2 = ips.indexOf(gameHost);
			if (index2 == -1) {
				index2 = ips.size();
				ips.add(gameHost);
			}

			String s = users + ":" + index1 + ":" + lobbyPort + ":" + index2 + ":" + gamePort + ":" + completeLobbyName(lobby);
			srv.setString("_" + String.format("%04d", id), s);
		}

		if (ips.size() > 0) {
			String IP_ASSOC = ips.get(0);
			for (int i = 1; i < ips.size(); i++)
				IP_ASSOC += ":" + ips.get(i);

			response.setString("ipc", IP_ASSOC);
		}

		if (srv.size() > 0)
			response.setStruct("srv", srv);

		try {
			executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery("SELECT * FROM " + DBV5Consts.TABLE_GAME_LOG + " WHERE game=" + SQLFormater.formatValue(getGameName()) + " AND (player1="
						+ SQLFormater.formatValue(gzid) + " OR player2=" + SQLFormater.formatValue(gzid) + ") AND winner!=-1 AND rated ORDER BY played_when DESC LIMIT 10")) {
					String[][] PHISTORY = new String[10][6];
					int k = 0;
					while (rs.next()) {
						String variant = rs.getString("variant");
						String player1 = rs.getString("player1");
						String player1Nick = URLUtil.urlDecode(rs.getString("player1_nick"));
						int oldRating1 = rs.getInt("old_rating1");
						int avatar1 = rs.getInt("avatar1");
						String player2 = rs.getString("player2");
						String player2Nick = URLUtil.urlDecode(rs.getString("player2_nick"));
						int oldRating2 = rs.getInt("old_rating2");
						int avatar2 = rs.getInt("avatar2");
						int winner = rs.getInt("winner");
						Timestamp playedWhen = rs.getTimestamp("played_when");

						String opponent;
						String opponentNick;
						int opponentAvatar;
						boolean win;
						if (player1.equals(gzid)) {
							opponent = player2;
							opponentNick = player2Nick;
							opponentAvatar = avatar2;
							win = winner == 0;
						} else {
							opponent = player1;
							opponentNick = player1Nick;
							opponentAvatar = avatar1;
							win = winner == 1;
						}

						PHISTORY[k][0] = opponent;
						PHISTORY[k][1] = variant;
						PHISTORY[k][2] = Long.toString(playedWhen.getTime() / 1000);
						PHISTORY[k][3] = win ? "1" : "0";
						PHISTORY[k][4] = Integer.toString(opponentAvatar);
						PHISTORY[k][5] = opponentNick;
						k++;
					}

					if (k > 0) {
						HashMap<String, Integer> opponents = new HashMap<>();
						for (int i = 0; i < k; i++) {
							String opponent = PHISTORY[i][0];
							Integer index = opponents.get(opponent);
							if (index == null)
								opponents.put(opponent, i);
							else
								PHISTORY[i][0] = Integer.toString(index);
						}

						GZStruct hst = new GZStruct();
						for (int i = 0; i < k; i++)
							hst.setString(Integer.toString(i), PHISTORY[i][0] + "|" + PHISTORY[i][1] + "|" + PHISTORY[i][2] + "|" + PHISTORY[i][3] + "|" + PHISTORY[i][4] + "|" + PHISTORY[i][5]);

						response.setStruct("hst", hst);
					}
				}

				return null;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		int registeredCount = getRegisteredUsers();

		int playedCount = 0;
		try {
			playedCount = executeTransaction((connection) -> {
				int result = 0;
				try (ResultSet rs = connection.executeQuery("SELECT count(*) FROM " + DBV5Consts.TABLE_GAME_LOG)) {
					if (rs.next())
						result = rs.getInt(1);
				}

				return result;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		response.setString("cstat", registeredCount + ":" + playedCount + ":" + online + ":" + playing);

		response.setString("i", user.getCompID());

		GZStruct zgp = new GZStruct();
		zgp.setBoolean("a", true); // criação de grupos habilitada
		if (group != null) {
			zgp.setString("n", group.getName());
			zgp.setString("d", Long.toString(group.getCreated().getTime() / 1000));
			zgp.setInt("m", group.getMemberCount());
		}
		response.setStruct("zgp", zgp);
	}

	private String completeLobbyName(Lobby lobby) {
		String name = lobby.getName();
		String country = lobby.getCountry();
		String groupID = lobby.getGroupID();
		String variant = lobby.getVariant();
		String tournamentID = lobby.getTournamentID();

		if (country != null)
			name = "@c@" + country + "@" + (name != null ? name : "");
		else if (groupID != null) {
			Group group = getGroupFromID(groupID);
			name = "@g@" + groupID + "@" + (name != null ? name : "") + "@" + group.getOwner();
		} else if (variant != null)
			name = "@y@" + variant + "@" + (name != null ? name : "");
		else if (tournamentID != null)
			name = "@t@" + tournamentID + "@" + (name != null ? name : "");

		return name;
	}

	public List<Variant> variants() {
		return new ArrayList<>(variants);
	}

	public void alertAllPlayers(String message) {
		for (Lobby lobby : lobbies)
			if (lobby.isOpen())
				lobby.alertAllPlayers(message);
	}

	public boolean isAdmin(String gzid) {
		return isSystem(gzid) || isRootAdmin(gzid) || getServerAdminLevel(gzid) > 0;
	}
	
	public boolean isAdminOfSomeGame(String gzid)
	{
		return isAdminOfSomeGame(gzid, false);
	}
	
	public boolean isAdminOfSomeGame(String gzid, boolean includeGroupRooms)
	{
		if (isSystem(gzid))
			return true;
		
		try {
			return executeTransaction((connection) -> {
				try (ResultSet rs = connection.executeQuery("SELECT * FROM " + DBV5Consts.TABLE_ADMINS + " WHERE active=1 AND gzid=" + SQLFormater.formatValue(gzid))) {
					while (rs.next())
					{
						Integer room = (Integer) rs.getObject("room");
						Lobby lobby = room != null ? getLobbyByID(room) : null;
						if (lobby != null && lobby.getGroupID() != null && !includeGroupRooms)
							continue;
						
						return true;
					}
				}
				
				return false;
			});
		} catch (SQLException e) {
			handleException(e);
		} catch (InterruptedException e) {
		}

		return false;
	}

	public boolean isAdmin(int lobby, String gzid) {
		if (lobby == -1)
			return isAdmin(gzid);

		return isAdmin(gzid) || getLobbyAdminLevel(lobby, gzid) > 0;
	}

	public void kickFromAllLobbies(Connection connection) {
		kickFromAllLobbies(connection.getGZID());
	}

	public void kickFromAllLobbies(String gzid) {
		post(() -> {
			for (Lobby lobby : lobbies)
				if (lobby.isOpen())
					lobby.kick(gzid);
		});
	}

	public void kickFromAllLobbies(Connection connection, Lobby exceptLobby) {
		kickFromAllLobbies(connection.getGZID(), exceptLobby.getID());
	}

	public void kickFromAllLobbies(String gzid, int exceptLobbyID) {
		post(() -> {
			for (Lobby lobby : lobbies)
				if (lobby.isOpen() && lobby.getID() != exceptLobbyID)
					lobby.kick(gzid);
		});
	}

	private void loadBadWords(SQLConnection connection) throws SQLException {
		badWords.clear();
		try (ResultSet rs = connection.selectAll(DBV5Consts.TABLE_BAD_WORDS)) {
			while (rs.next()) {
				String word = rs.getString("word");
				badWords.add(normalizeWord(word));
			}
		}
	}

	public boolean isBadWord(String word) {
		return badWords.contains(normalizeWord(word));
	}

	public static String normalizeWord(String word) {
		word = word.toLowerCase();
		String result = "";

		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			switch (c) {
				case 'Ã':
				case 'ã':
				case 'Á':
				case 'á':
				case 'À':
				case 'à':
				case 'Â':
				case 'â':
				case '4':
				case '@':
					result += 'a';
					break;

				case 'É':
				case 'é':
				case 'È':
				case 'è':
				case 'Ê':
				case 'ê':
				case '3':
					result += 'e';
					break;

				case 'Í':
				case 'í':
				case 'Ì':
				case 'ì':
				case 'Î':
				case 'î':
				case '1':
				case '!':
					result += 'i';
					break;

				case 'Õ':
				case 'õ':
				case 'Ó':
				case 'ó':
				case 'Ò':
				case 'ò':
				case 'Ô':
				case 'ô':
				case '0':
					result += 'o';
					break;

				case 'Ú':
				case 'ú':
				case 'Ù':
				case 'ù':
				case 'Û':
				case 'û':
					result += 'u';
					break;

				case 'Ñ':
				case 'ñ':
					result += 'n';
					break;

				case '$':
					result += 's';
					break;
					
				case 'C':
				case 'c':
				case 'K':
				case 'k':
					result += 'c';
					break;

				default:
					result += c;
			}
		}

		return result;
	}

}
