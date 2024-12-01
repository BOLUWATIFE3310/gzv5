package gz.server.net.billiards;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import common.util.DebugUtil;
import common.util.RandomUtil;
import gz.common.logic.Game.StopReason;
import gz.common.logic.MultiplayerGame;
import gz.common.logic.Player;
import gz.common.logic.billiards.BilliardsGame;
import gz.common.logic.billiards.BilliardsGame.Ball;
import gz.common.logic.billiards.BilliardsGame.Cue;
import gz.common.logic.billiards.BilliardsGame.GameState;
import gz.common.logic.billiards.BilliardsGame.Shot;
import gz.common.logic.billiards.BilliardsGameController;
import gz.common.logic.billiards.BilliardsPlayer;
import gz.common.logic.billiards.Point2D;
import gz.common.logic.billiards.brazilian.BrazilianPoolGame;
import gz.common.logic.billiards.carom.CaromGame;
import gz.common.logic.billiards.eightball.EightBallGame;
import gz.common.logic.billiards.nineball.NineBallGame;
import gz.common.logic.billiards.pyramid.PyramidGame;
import gz.common.logic.billiards.snooker.SnookerGame;
import gz.common.logic.billiards.straight.StraightGame;
import gz.common.logic.billiards.svoi.PyramidSvoiGame;
import gz.server.net.Connection;
import gz.server.net.Container;
import gz.server.net.Container.Inventory;
import gz.server.net.Lobby;
import gz.server.net.LobbyGame;
import gz.util.GZStruct;
import gz.util.GZUtils;

@SuppressWarnings("unused")
public class BilliardsLobbyGame extends LobbyGame {

	private static final int MINIMUM_TURN_NUMBER_TO_RATED = 4;

	protected class BilliardsController extends Controller implements BilliardsGameController {

		public BilliardsController() {
			super();
		}

		@Override
		public void onChangeTurn(int turn) {
			super.onChangeTurn(turn);
		}

		@Override
		public void onStart() {
			if (!firstGame)
				getGame().rotateLeft();
			else
				firstGame = false;

			super.onStart();
		}

	}

	public class BilliardsSeat extends Seat {

		protected BilliardsSeat(int index, Connection user) {
			super(index, user);
		}

		@Override
		public BilliardsConnection getConnection() {
			return (BilliardsConnection) super.getConnection();
		}

		@Override
		public BilliardsPlayer getPlayer() {
			return (BilliardsPlayer) super.getPlayer();
		}

		public int getRating() {
			return super.getRating();
		}

		public int getRating2() {
			return super.getRating2();
		}

	}

	protected static final boolean DEBUG = true;

	private boolean firstGame = true;
	private boolean training;

	protected boolean withGuide;
	protected boolean withSpin;
	protected boolean playAgainstComputer;
	protected boolean v5Fix;
	protected boolean usePowerfulShots;

	private int rnd;
	private String sqk;
	private int[] sqc;
	private int tn;
	private boolean animating;

	public BilliardsLobbyGame() {
		super();
	}

	@Override
	protected void afterClose() {
		super.afterClose();
	}

	@Override
	protected void beforeClose() {
		super.beforeClose();
	}

	private String buildShot(Shot shot) {
		return Integer.toString(shot.getV(), 16) + "{" + Integer.toString(shot.getS(), 16) + "{" + Integer.toString(shot.getR_MV(), 16) + "{" + Integer.toString(shot.getR_MS(), 16) + "{"
				+ Integer.toString(shot.getR_HV(), 16) + "{" + Integer.toString(shot.getR_HS(), 16) + "{" + Integer.toString(shot.getRI_H(), 16) + "{" + Integer.toString(shot.getRI_V(), 16);
	}

	private void checkGameState(BilliardsGame game, BilliardsConnection user, Seat seat, GZStruct request) {
		String v = request.getString("v");
		String[] v1 = v.split(";");

		int[] ballIndices = null;
		Point2D[] ballPositions = null;
		int[] pottedBallIndices = null;
		int[] pottedBallSlots = null;
		int[] hitBallIndices = null;

		int cueBallIndex = Integer.parseInt(v1[0], 16);

		if (DEBUG && DebugUtil.DEBUG_MODE) {
			System.out.println("Chosen Ball: " + cueBallIndex);
			System.out.println();
		}

		if (v1.length > 1) {
			if (!v1[1].equals("")) {
				if (DEBUG && DebugUtil.DEBUG_MODE)
					System.out.println("Balls:");

				String[] v11 = v1[1].split("\\{");

				ballIndices = new int[v11.length];
				ballPositions = new Point2D[v11.length];

				for (int i = 0; i < v11.length; i++) {
					String[] s = v11[i].split("}");
					int ballIndex = Integer.parseInt(s[0], 16) - sqc[i];
					int x = Integer.parseInt(s[1], 16) - sqc[i + 1] * 1000;
					int y = Integer.parseInt(s[2], 16) - sqc[i + 2] * 1000;

					ballIndices[i] = ballIndex;
					ballPositions[i] = new Point2D(x, y);

					if (DEBUG && DebugUtil.DEBUG_MODE) {
						System.out.println("\tBall Index: " + ballIndex);
						System.out.println("\tBall X: " + x);
						System.out.println("\tBall Y: " + y);

						System.out.println();
					}
				}
			}

			if (v1.length > 2) {
				if (!v1[2].equals("")) {
					if (DEBUG && DebugUtil.DEBUG_MODE)
						System.out.println("Pocketed Balls:");

					String[] pk = v1[2].split("\\{");

					pottedBallIndices = new int[pk.length];
					pottedBallSlots = new int[pk.length];

					for (int i = 0; i < pk.length; i++) {
						String[] s = pk[i].split("}");

						int ballIndex = Integer.parseInt(s[0], 16) - sqc[i + 2];
						int pocketIndex = Integer.parseInt(s[1]);

						pottedBallIndices[i] = ballIndex;
						pottedBallSlots[i] = pocketIndex;

						if (DEBUG && DebugUtil.DEBUG_MODE) {
							System.out.println("\tBall: " + ballIndex);
							System.out.println("\tPocket: " + pocketIndex);

							System.out.println();
						}
					}
				}

				if (v1.length > 3)
					if (!v1[3].equals("")) {
						if (DEBUG && DebugUtil.DEBUG_MODE)
							System.out.println("Hit Balls:");

						String[] ct = v1[3].split("\\{");

						hitBallIndices = new int[ct.length];

						for (int i = 0; i < ct.length; i++) {
							int ballIndex = Integer.parseInt(ct[i], 16);

							hitBallIndices[i] = ballIndex;

							if (DEBUG && DebugUtil.DEBUG_MODE) {
								System.out.println("\t" + i + ": " + ballIndex);
								System.out.println();
							}
						}
					}
			}
		}

		game.updateGameState(cueBallIndex, ballIndices, ballPositions, pottedBallIndices, pottedBallSlots, hitBallIndices);
		tn++;
		animating = false;
		user.wachingAnimation = false;

		notifyTurnState(game, user, seat);

		for (Connection c : connections) {
			BilliardsConnection bc = (BilliardsConnection) c;
			if (bc.equals(user))
				continue;

			bc.updated = false;

			if (bc.waitingForState)
				notifyTurnState(game, bc, getSeat(bc));
		}
	}

	@Override
	protected MultiplayerGame createGame() {
		rnd = RandomUtil.randomRange(0, Integer.MAX_VALUE);
		sqk = GZUtils.hmx(Integer.toString(rnd));

		sqc = new int[32];
		for (int i = 0; i < sqc.length; i++)
			sqc[i] = Integer.parseInt(sqk.substring(i, i + 1), 16);

		switch (variant) {
			case "8ball":
				return new EightBallGame(new BilliardsController(), sqc);

			case "9ball":
				return new NineBallGame(new BilliardsController(), sqc);

			case "snooker":
				return new SnookerGame(new BilliardsController(), sqc);

			case "straight":
				return new StraightGame(new BilliardsController(), sqc, false);

			case "pyramid":
				return new PyramidGame(new BilliardsController(), sqc);

			case "svoi":
				return new PyramidSvoiGame(new BilliardsController(), sqc);

			case "carom":
				return new CaromGame(new BilliardsController(), sqc);

			case "gzpool":
				return new StraightGame(new BilliardsController(), sqc, true);

			case "brpool":
				return new BrazilianPoolGame(new BilliardsController(), sqc);
		}

		return null;
	}

	@Override
	protected Player[] createPlayers() {
		return new BilliardsPlayer[getMaxSeatCount()];
	}

	@Override
	protected Seat createSeat(int seatIndex, Connection user) {
		return new BilliardsSeat(seatIndex, user);
	}

	@Override
	protected Seat[] createSeats() {
		return new BilliardsSeat[getMaxSeatCount()];
	}

	@Override
	protected void defaultConfig() {
		super.defaultConfig();
	}

	@Override
	protected void dumpGameState() {
		ArrayList<String> messages = new ArrayList<>();
		messages.add("[game] " + variant);
		messages.add(getGame().toString());
		messages.add("");
		getContainer().logToErr(toString(), messages.toArray(new String[] {}));
	}

	@Override
	public BilliardsContainer getContainer() {
		return (BilliardsContainer) super.getContainer();
	}

	@Override
	protected BilliardsGame getGame() {
		return (BilliardsGame) super.getGame();
	}

	private String getGameState() {
		return getGameState(getGame().getCurrentState());
	}

	private String getGameState(GameState state) {
		BilliardsGame game = getGame();

		if (!game.isGameModified())
			return variant;

		int bnm = game.getBNM();
		String[] arr = new String[bnm];

		for (int i = 0; i < bnm; i++) {
			Ball ball = state.getBall(i);
			int ballIndex = ball.getIndex();
			int x = ball.getX();
			int y = ball.getY();
			arr[i] = Integer.toString(ballIndex + sqc[i], 16) + "}" + Integer.toString(x + sqc[i + 1] * 1000, 16) + "}" + Integer.toString(y + sqc[i + 2] * 1000, 16) + "}"
					+ (ball.isVisible() ? "1" : "0");
		}

		return GZUtils.join(arr, "{");
	}

	@Override
	public BilliardsSeat getSeat(Connection user) {
		return (BilliardsSeat) super.getSeat(user);
	}

	@Override
	protected String getTurnStr(int turn) {
		return getVariantPrefix() + "_" + rnd;
	}

	private String getVariantPrefix() {
		switch (variant) {
			case "gzpool":
				return "gz";

			case "snooker":
				return "sn";

			case "carom":
				return "cr";

			case "pyramid":
			case "svoi":
				return "ru";

			case "8ball":
			case "brpool":
				return "am";

			case "9ball":
				return "nb";

			case "straight":
				return "am";
		}

		return "?";
	}

	public boolean isPlayingAgainstComputer() {
		return playAgainstComputer;
	}

	@Override
	public boolean isSinglePlayer() {
		return training;
	}

	public boolean isTraining() {
		return training;
	}

	public boolean isWithGuide() {
		return withGuide;
	}

	private String makeBallSet(int bnm, BilliardsPlayer player) {
		int[] ballSet = player.getBallSet();
		if (ballSet == null) {
			String result = "1";
			for (int i = 1; i < bnm; i++)
				result += "_" + i;

			return result;
		}

		if (ballSet.length == 0)
			return "";

		String result = Integer.toString(ballSet[0]);
		for (int i = 1; i < ballSet.length; i++)
			result += "_" + Integer.toString(ballSet[i]);

		return result;
	}

	@Override
	protected void notifyGameState(Connection connection, GZStruct gd) {
		BilliardsConnection bc = (BilliardsConnection) connection;
		bc.turnNumber = tn;
		bc.waitingForState = false;
		bc.updated = true;

		BilliardsPlayer player1 = seats[0] != null ? (BilliardsPlayer) seats[0].getPlayer() : null;
		BilliardsPlayer player2 = seats[1] != null ? (BilliardsPlayer) seats[1].getPlayer() : null;

		int seatIndex = getSeatIndex(connection);
		gd.setBoolean("spmcm", seatIndex == -1);
		gd.setBoolean("ef", withGuide); // play with guide
		gd.setBoolean("ez", withSpin); // spin on
		gd.setBoolean("v5fix", v5Fix); // using v5 fixed engine
		gd.setBoolean("pwrs", usePowerfulShots); // using powerful shots
		gd.setString("le", getGameState()); // game state

		BilliardsGame game = getGame();
		int currentTurn = game.getCurrentTurn();
		boolean selecting = game.isRunning() && game.getCurrentState().isSelecting();

		if (seatIndex != -1) {
			if (game.getCurrentTurn() == seatIndex) {
				gd.setString("f", "nx");
				gd.setBoolean("fb", selecting); // can select all balls?
				gd.setInt("fh", game.getHP()); // hand ball mode
			}
		} else
			gd.setInt("fh", 0);

		if (game.isRunning()) {
			if (seatIndex != -1) {
				gd.setInt("ccr", getCueModel(seats[seatIndex] != null ? seats[seatIndex].getConnection() : null)); // player
																													// cue
																													// model
				gd.setInt("cop", getCueModel(seats[1 - seatIndex] != null ? seats[1 - seatIndex].getConnection() : null)); // opponent
																															// cue
																															// model
			} else {
				gd.setInt("ccr", getCueModel(seats[0] != null ? seats[0].getConnection() : null)); // player
																									// cue
																									// model
				gd.setInt("cop", getCueModel(seats[1] != null ? seats[1].getConnection() : null)); // opponent
																									// cue
																									// model
			}

			Shot shot = game.getShot();
			if (shot != null) {
				gd.setString("v", Integer.toString(game.getCueBallIndex(), 16) + "{" + buildShot(shot));
				gd.setString("f", "ht");
				bc.wachingAnimation = true;
			}

			int hp = game.getHP();
			if (hp == 0 && !selecting) {
				Cue cue = game.getCue();
				gd.setBoolean("scuf", true);
				gd.setInt("i", cue.getCueBallIndex());
				gd.setString("p", Integer.toString(cue.getX(), 16) + "_" + Integer.toString(cue.getY(), 16));
				gd.setInt("a", cue.getAngle());
			}

			if (training && seats[1 - currentTurn] != null && seats[1 - currentTurn].getConnection() == connection && seats[currentTurn] != null && seats[currentTurn].getConnection() == null) {
				gd.setBoolean("btp", true);
				gd.setString("bt", makeBallSet(game.getBNM(), (BilliardsPlayer) seats[currentTurn].getPlayer()));
				gd.setString("f", "nx");
				gd.setBoolean("fb", selecting); // can select all balls?
				gd.setInt("fh", hp); // hand ball mode
			}

			if (variant.equals("9ball") || variant.equals("snooker") && ((BilliardsPlayer) seats[currentTurn].getPlayer()).getBallIcon() < 9)
				gd.setInt("hti", game.getLeastVisibleBall());
		}

		if (player1 != null) {
			List<Integer> ballSet1 = player1.pottedBalls();
			if (ballSet1.size() > 0)
				gd.setString("pk1", GZUtils.join(ballSet1.toArray(), "_"));

			int ballIndex1 = player1.getBallIcon();
			if (ballIndex1 != -1 && (variant.equals("8ball") || variant.equals("brpool") || variant.equals("snooker") && currentTurn == 0))
				gd.setInt("bic1", ballIndex1);
			else
				gd.setInt("bic1", 0);

			gd.setInt("scr1", player1.getScore()); // player
			// 1
			// score
		}

		if (player2 != null) {
			List<Integer> ballSet2 = player2.pottedBalls();
			if (ballSet2.size() > 0)
				gd.setString("pk2", GZUtils.join(ballSet2.toArray(), "_"));

			int ballIndex2 = player2.getBallIcon();
			if (ballIndex2 != -1 && (variant.equals("8ball") || variant.equals("brpool") || variant.equals("snooker") && currentTurn == 1))
				gd.setInt("bic2", ballIndex2);
			else
				gd.setInt("bic2", 0);

			gd.setInt("scr2", player2.getScore()); // player
			// 2
			// score
		}

		gd.setInt("tn", tn);
		gd.setInt("sn", 0);
	}

	private int getCueModel(int rating) {
		if (rating < 5)
			return 0;

		if (rating < 10)
			return 1;

		if (rating < 25)
			return 2;

		if (rating < 50)
			return 3;

		if (rating < 100)
			return 4;

		if (rating < 200)
			return 5;

		if (rating < 300)
			return 6;

		if (rating < 400)
			return 7;

		if (rating < 500)
			return 8;

		if (rating < 1000)
			return 9;

		if (rating < 2000)
			return 10;

		if (rating < 3000)
			return 11;

		if (rating < 4000)
			return 12;

		if (rating < 5000)
			return 13;

		if (rating < 6000)
			return 14;

		if (rating < 7000)
			return 15;

		if (rating < 8000)
			return 16;

		if (rating < 9000)
			return 17;

		if (rating < 10000)
			return 18;

		return 19;
	}
	
	private int getCueModel(Connection connection) {
		if (connection == null)
			return 0;
		
		Inventory inventory = connection.getActiveInventory("cue");
		if (inventory == null)
			return getCueModel(connection.getRating() / 1000);
		
		String item = inventory.getItem();
		if (!item.startsWith("cue"))
			return getCueModel(connection.getRating() / 1000);
		
		try {
			int number = Integer.parseInt(item.substring(3));
			return number;
		} catch (NumberFormatException e) {
		}
		
		return getCueModel(connection.getRating() / 1000);
	}

	private void notifyTurnState(BilliardsGame game, BilliardsConnection connection, Seat seat) {
		BilliardsConnection bc = connection;
		bc.waitingForState = false;
		bc.updated = true;
		bc.turnNumber = tn;

		int seatIndex = seat != null ? seat.getIndex() : -1;

		BilliardsPlayer player1 = seats[0] != null ? (BilliardsPlayer) seats[0].getPlayer() : null;
		BilliardsPlayer player2 = seats[1] != null ? (BilliardsPlayer) seats[1].getPlayer() : null;

		GameState state = game.getCurrentState();
		// GameState state = game.getState(connection == null ?
		// ((BilliardsConnection) seats[1 - myIndex].getConnection()).turnNumber
		// : connection.turnNumber);

		Ball whiteBall = state.getBall(state.getCueBallIndex());
		int hp = state.getHandBallMode();
		int currentTurn = state.getCurrentTurn();
		// user = seats[currentTurn].getUser();
		GZStruct gd = new GZStruct();

		gd.setString("le", getGameState(state));

		if (game.isRunning()) {
			boolean selecting = game.getCurrentState().isSelecting();

			if (seatIndex != -1) {
				gd.setInt("ccr", getCueModel(seats[seatIndex] != null ? seats[seatIndex].getConnection() : null)); // player
																													// cue
																													// model
				gd.setInt("cop", getCueModel(seats[1 - seatIndex] != null ? seats[1 - seatIndex].getConnection() : null)); // opponent
																															// cue
																															// model
			} else {
				gd.setInt("ccr", getCueModel(seats[0] != null ? seats[0].getConnection() : null)); // player
																									// cue
																									// model
				gd.setInt("cop", getCueModel(seats[1] != null ? seats[1].getConnection() : null)); // opponent
																									// cue
																									// model
			}

			Shot shot = game.getShot();
			if (shot != null) {
				gd.setString("v", Integer.toString(game.getCueBallIndex(), 16) + "{" + buildShot(shot));
				gd.setString("f", "ht");
				bc.wachingAnimation = true;
			}

			if (hp == 0 && !selecting) {
				Cue cue = game.getCue();
				gd.setBoolean("scuf", true);
				gd.setInt("i", cue.getCueBallIndex());
				gd.setString("p", Integer.toString(cue.getX(), 16) + "_" + Integer.toString(cue.getY(), 16));
				gd.setInt("a", cue.getAngle());
			}

			if (seat != null && (training || seat.getPlayer().isMyTurn())) {
				gd.setInt("fh", hp); // hand ball mode
				gd.setString("f", "nx");
				gd.setBoolean("fb", selecting); // can choose a ball?
			}

			if (variant.equals("9ball") || variant.equals("snooker") && ((BilliardsPlayer) seats[currentTurn].getPlayer()).getBallIcon() < 9)
				gd.setInt("hti", state.getLeastVisibleBall());
		}

		if (currentTurn != MultiplayerGame.NO_TURN)
			gd.setInt("pe", currentTurn + 1);

		if (player1 != null) {
			List<Integer> ballSet1 = player1.pottedBalls();
			if (ballSet1.size() > 0)
				gd.setString("pk1", GZUtils.join(ballSet1.toArray(), "_"));

			int ballIndex1 = player1.getBallIcon();
			if (ballIndex1 != -1 && (variant.equals("8ball") || variant.equals("brpool") || variant.equals("snooker") && currentTurn == 0))
				gd.setInt("bic1", ballIndex1);
			else
				gd.setInt("bic1", 0);

			gd.setInt("scr1", player1.getScore()); // player
			// 1
			// score
		}

		if (player2 != null) {
			List<Integer> ballSet2 = player2.pottedBalls();
			if (ballSet2.size() > 0)
				gd.setString("pk2", GZUtils.join(ballSet2.toArray(), "_"));

			int ballIndex2 = player2.getBallIcon();
			if (ballIndex2 != -1 && (variant.equals("8ball") || variant.equals("brpool") || variant.equals("snooker") && currentTurn == 1))
				gd.setInt("bic2", ballIndex2);
			else
				gd.setInt("bic2", 0);

			if (training && seats[1 - currentTurn] != null && seats[1 - currentTurn].getConnection() == connection && seats[currentTurn] != null && seats[currentTurn].getConnection() == null) {
				gd.setBoolean("btp", true);
				gd.setString("bt", makeBallSet(game.getBNM(), (BilliardsPlayer) seats[currentTurn].getPlayer()));
			}

			gd.setInt("scr2", player2.getScore()); // player
			// 2
			// score
		}

		gd.setInt("tn", tn);
		gd.setInt("sn", 0);

		GZStruct response = new GZStruct();
		response.setStruct("gd", gd);
		if (connection == null) {
			Seat otherSeat = seats[1 - seatIndex];
			connection = (BilliardsConnection) (otherSeat != null ? otherSeat.getConnection() : null);
		}

		if (connection != null)
			connection.postBlock(response);
	}

	@Override
	protected void onConnectionAdded(Connection connection, GZStruct response) {
		super.onConnectionAdded(connection, response);
	}

	@Override
	protected void onStartGame() {
		tn = 0;
		animating = false;

		for (Connection connection : connections) {
			BilliardsConnection bc = (BilliardsConnection) connection;
			bc.turnNumber = 0;
			bc.updated = false;
			bc.waitingForState = false;
			bc.wachingAnimation = false;
		}

		super.onStartGame();
	}

	@Override
	protected void onStopGame(StopReason reason) {
		super.onStopGame(reason);
	}

	@Override
	protected void open(Container server, Lobby lobby, int number, String variant, boolean allowWatchers, int[] options, Connection... players) throws IOException {
		if (players.length == 2) {
			training = players[0] == players[1];
			super.open(server, lobby, number, variant, allowWatchers, options, players);
		} else {
			training = true;
			Connection[] players1 = new Connection[2];
			players1[0] = players[0];
			players1[1] = null;
			super.open(server, lobby, number, variant, allowWatchers, options, players1);
		}

		BilliardsContainer boardServer = (BilliardsContainer) server;
	}

	@Override
	protected void parseGameData(Connection u, String opcode, GZStruct request) throws Throwable {
		BilliardsConnection user = (BilliardsConnection) u;
		BilliardsGame game = getGame();

		// int tn = request.getInt("tn");
		// int sn = request.getInt("sn");

		if (DEBUG && DebugUtil.DEBUG_MODE)
			System.out.println("tn: " + this.tn);

		switch (opcode) {
			case "prp":
			case "cj": {
				user.wachingAnimation = false;

				if (!animating) {
					if (!user.updated)
						notifyTurnState(game, user, getSeat(user));
				} else
					user.waitingForState = true;

				break;
			}

			case "cu": {
				if (!game.isRunning())
					break;

				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (!training && !seat.getPlayer().isMyTurn())
					break;

				int a = request.getInt("a");
				game.updateCue(a);

				if (DEBUG && DebugUtil.DEBUG_MODE) {
					System.out.println("Cue State");
					System.out.println("\tCue Angle: " + a);
				}

				GZStruct gd = new GZStruct();
				gd.setString("f", "su");
				gd.setInt("tn", this.tn);
				gd.setInt("sn", 0);
				GZStruct response = new GZStruct();
				response.setStruct("gd", gd);
				user.postBlock(response);

				gd = new GZStruct();
				gd.setInt("a", a);
				gd.setString("f", "cu");
				gd.setInt("tn", this.tn);
				gd.setInt("sn", 0);

				response = new GZStruct();
				response.setStruct("gd", gd);
				new BroadCast(response, standByConnections(), user).send();

				// gameModified = true;

				break;
			}

			case "cuf": { // cue state
				if (!game.isRunning())
					break;

				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (!training && !seat.getPlayer().isMyTurn())
					break;

				int a = request.getInt("a");
				String v = request.getString("v");
				String[] v1 = v.split("_");

				int cueBallIndex = Integer.parseInt(v1[0]);
				int x = Integer.parseInt(v1[1], 16);
				int y = Integer.parseInt(v1[2], 16);

				game.updateCueBall(cueBallIndex, x, y, a);
				game.updateBall(cueBallIndex, x, y);

				if (DEBUG && DebugUtil.DEBUG_MODE) {
					System.out.println("Cue State");
					System.out.println("\tBall Index: " + cueBallIndex);
					System.out.println("\tx: " + x);
					System.out.println("\ty: " + y);
					System.out.println("\tCue Angle: " + a);
				}

				GZStruct gd = new GZStruct();
				gd.setString("f", "su");
				gd.setInt("tn", this.tn);
				gd.setInt("sn", 0);
				GZStruct response = new GZStruct();
				response.setStruct("gd", gd);
				user.postBlock(response);

				gd = new GZStruct();
				// gd.setString("le", getGameState(game.getCurrentState(),
				// new
				// int[] { 0 }));
				gd.setInt("a", a);
				gd.setInt("i", cueBallIndex);
				gd.setString("p", Integer.toString(x, 16) + "_" + Integer.toString(y, 16));
				gd.setString("f", "cuf");
				gd.setInt("tn", this.tn);
				gd.setInt("sn", 0);
				response = new GZStruct();
				response.setStruct("gd", gd);
				new BroadCast(response, standByConnections(), user).send();

				// gameModified = true;

				break;
			}

			case "ht": { // shot
				if (!game.isRunning())
					break;

				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (!training && !seat.getPlayer().isMyTurn())
					break;

				String v = request.getString("v");
				String[] v1 = v.split("\\{");

				int v100 = Integer.parseInt(v1[0], 16);
				int s = Integer.parseInt(v1[1], 16);
				int r_mv = Integer.parseInt(v1[2], 16);
				int r_ms = Integer.parseInt(v1[3], 16);
				int r_hv = Integer.parseInt(v1[4], 16);
				int r_hs = Integer.parseInt(v1[5], 16);
				int ri_h = Integer.parseInt(v1[6], 16);
				int ri_v = Integer.parseInt(v1[7], 16);

				if (DEBUG && DebugUtil.DEBUG_MODE) {
					System.out.println("Shot:");
					System.out.println("\tv: " + v100 / 100);
					System.out.println("\ts: " + s);
					System.out.println("\tr_mv: " + r_mv / 100);
					System.out.println("\tr_ms: " + r_ms);
					System.out.println("\tr_hv: " + r_hv / 100);
					System.out.println("\tr_hs: " + r_hs);
					System.out.println("\tri_h: " + ri_h);
					System.out.println("\tri_v: " + ri_v);
				}

				game.shot(v100, s, r_mv, r_ms, r_hv, r_hs, ri_h, ri_v);
				animating = true;
				user.wachingAnimation = true;

				v = Integer.toString(game.getCueBallIndex(), 16) + "{" + v;

				for (Connection c : connections) {
					BilliardsConnection bc = (BilliardsConnection) c;
					if (bc.equals(user))
						continue;

					bc.wachingAnimation = true;

					GZStruct gd = new GZStruct();
					gd.setString("v", v);
					gd.setString("f", "ht");
					gd.setInt("tn", this.tn);
					gd.setInt("sn", 0);

					GZStruct response = new GZStruct();
					response.setStruct("gd", gd);
					bc.postBlock(response);
				}

				break;
			}

			case "prm": { // game state from current player
				if (!game.isRunning())
					break;

				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (!training && !seat.getPlayer().isMyTurn())
					break;

				checkGameState(game, user, seat, request);

				break;
			}

			default:
				super.parseGameData(user, opcode, request);
		}
	}

	@Override
	protected void parseOptions(int[] options) {
		withGuide = options[0] == 1;

		if (training) {
			playAgainstComputer = options[1] == 1;
			withSpin = true;
		} else {
			playAgainstComputer = false;
			withSpin = options[1] == 1;
		}

		v5Fix = options.length > 2 ? options[2] == 1 : true;
		usePowerfulShots = options.length > 3 ? options[3] == 1 : true;

		timePerMove = DEFAULT_TIME_PER_TURN_SEC + TIME_PER_MOVE_TOLERANCE;
	}

	protected List<Connection> standByConnections() {
		BilliardsGame game = getGame();
		ArrayList<Connection> result = new ArrayList<>();
		for (Connection connection : connections) {
			BilliardsConnection bc = (BilliardsConnection) connection;
			if (!bc.wachingAnimation)
				result.add(bc);
		}

		return result;
	}

	protected boolean validateMaxRating(int maxRating) {
		BilliardsContainer container = getContainer();

		int min = container.getMinRating();
		int max = container.getMaxRating();

		if (maxRating < min || maxRating > max)
			return false;

		return true;
	}

	protected boolean validateMinRating(int minRating) {
		BilliardsContainer container = getContainer();

		int min = container.getMinRating();
		int max = container.getMaxRating();

		if (minRating < min || minRating > max)
			return false;

		return true;
	}

	protected boolean isReachedTheMinimumConditionalToRated() {
		return true;
		// return getGame().getTurnNumber() >= MINIMUM_TURN_NUMBER_TO_RATED;
	}

	protected int getTurnCount() {
		return getGame().getTurnNumber();
	}

	public boolean isWithSpin() {
		return withSpin;
	}

	public boolean usingV5Fix() {
		return v5Fix;
	}

	public boolean usingPowerfulShots() {
		return usePowerfulShots;
	}

}
