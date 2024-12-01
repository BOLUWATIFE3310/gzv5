package gz.server.net.boards;

import java.io.IOException;
import java.util.List;

import gz.common.logic.Game.StopReason;
import gz.common.logic.MultiplayerGame;
import gz.common.logic.boards.BoardGame;
import gz.common.logic.boards.BoardGameController;
import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPlayer;
import gz.server.net.Connection;
import gz.server.net.Container;
import gz.server.net.Lobby;
import gz.server.net.LobbyGame;
import gz.util.GZStruct;

@SuppressWarnings("unused")
public abstract class BoardLobbyGame extends LobbyGame {

	private static final int MINIMUM_MOVE_HISTORY_COUNT_TO_RATED = 4;

	protected class BoardController extends Controller implements BoardGameController {

		public BoardController() {
			super();
		}

		@Override
		public void onChangeTurn(int turn) {
			super.onChangeTurn(turn);

			BoardGame game = getGame();
			if (DEBUG)
				logToOut(System.lineSeparator() + game.toString());

			if (!gameSetup)
				for (Connection connection : connections) {
					GZStruct response = new GZStruct();
					GZStruct gd = new GZStruct();

					if (timePerMove > 0) {
						int time = game.getCurrentTimePerTurn() - TIME_PER_MOVE_TOLERANCE;
						gd.setInt("ctmv", time > 0 ? time : 0);
					}

					if (drawOffering && !connection.equals(lastMoveUser))
						gd.setString("draw", "pm");
					else if (drawRejected && whosOfferingDraw != null && connection.equals(whosOfferingDraw.getConnection())) {
						whosOfferingDraw = null;
						drawRejected = false;
						gd.setString("draw", "rj");
					}

					int currentTurn = game.getCurrentTurn();
					if (currentTurn != MultiplayerGame.NO_TURN)
						gd.setInt("pe", currentTurn + 1);

					for (int i = 0; i < seats.length; i++) {
						Seat seat = seats[i];
						if (seat == null)
							continue;

						int time = seats[i].getPlayer().getCurrentTime() - MATCH_TIME_TOLERANCE;

						if (matchTime > 0)
							gd.setInt("prt" + (i + 1), time > 0 ? time : 0);
					}

					response.setStruct("gd", gd);
					connection.postBlock(response);
				}

			lastMove = null;
			lastMoveUser = null;
			drawOffering = false;
		}

		@Override
		public void onPause(int time) {
			if (DEBUG)
				logToOut("Game paused.");
		}

		@Override
		public void onPauseTimeOut() {
			if (DEBUG)
				logToOut("Game resumed after pause timeout.");

			if (whosOfferingDraw != null) {
				Connection user = null;
				for (Seat seat : seats) {
					if (seat == null || seat.equals(whosOfferingDraw))
						continue;

					Connection user1 = seat.getConnection();
					if (user1 != null) {
						user = user1;
						break;
					}
				}

				whosOfferingDraw = null;
			}

			if (whosSuggestedUndoMove != null) {
				Connection user = null;
				for (Seat seat : seats) {
					if (seat == null || seat.equals(whosSuggestedUndoMove))
						continue;

					Connection user1 = seat.getConnection();
					if (user1 != null) {
						user = user1;
						break;
					}
				}

				whosSuggestedUndoMove = null;
			}
		}

		@Override
		public void onResume() {
			if (DEBUG)
				logToOut("Game resumed.");

			whosOfferingDraw = null;
			whosSuggestedUndoMove = null;
		}

		@Override
		public void onStart() {
			lastMove = null;
			lastMoveUser = null;
			drawOffering = false;

			if (swapSides && !firstGame)
				getGame().rotateLeft();

			firstGame = false;

			super.onStart();
		}

		@Override
		public void onUndoLastMove() {
			if (DEBUG)
				logToOut("Game undo last move.");
		}

	}

	public abstract class BoardSeat extends Seat {

		protected BoardSeat(int index, Connection user) {
			super(index, user);
		}

		@Override
		public BoardPlayer getPlayer() {
			return (BoardPlayer) super.getPlayer();
		}

		public int getRating() {
			return super.getRating();
		}

		public int getRating2() {
			return super.getRating2();
		}

	}

	protected class PauseGameQuestion extends SeatQuestion {

		private int time;

		public PauseGameQuestion() {
			super();
		}

		public int getTime() {
			return time;
		}

		public void open(int time, Seat questioner, List<Seat> questioneds, QuestionTimeout<Seat, Seat> callback) {
			super.open(questioner, questioneds, RESPONSE_INTERVAL, callback);

			this.time = time;
		}

		@Override
		public void open(Seat questioner, List<Seat> questioneds, QuestionTimeout<Seat, Seat> callback) {
			open(10, questioner, questioneds, callback);
		}

	}

	private static final boolean DEBUG = false;

	public static final int RATING_MARGIN = 200;

	public static final int CHALLANGE = 2;

	private boolean firstGame;
	private int gameType;
	private boolean hasRatingLimit = false;
	private int minRating;
	private int maxRating;
	private boolean activeHelp = true;
	private boolean swapSides = true;

	private Connection lastMoveUser;
	private String lastMove;
	private boolean drawOffering;
	private boolean drawRejected;

	private Seat whosOfferingDraw;
	private Seat whosSuggestedUndoMove;

	protected SeatQuestion drawGameQuestion;

	protected BoardLobbyGame() {
		super();
	}

	@Override
	protected void afterClose() {
		super.afterClose();

		drawGameQuestion = null;
		whosOfferingDraw = null;
		whosSuggestedUndoMove = null;
	}

	@Override
	protected void beforeClose() {
		drawGameQuestion.terminate();

		super.beforeClose();
	}

	@Override
	protected void defaultConfig() {
		super.defaultConfig();
	}

	@Override
	protected void dumpGameState() {
		getContainer().logToErr(toString(), "variant: " + variant + "\n" + getGame().toString());
	}

	@Override
	public BoardContainer getContainer() {
		return (BoardContainer) super.getContainer();
	}

	@Override
	protected BoardGame getGame() {
		return (BoardGame) super.getGame();
	}

	public int getGameType() {
		return gameType;
	}

	@Override
	public BoardSeat getSeat(Connection user) {
		return (BoardSeat) super.getSeat(user);
	}

	public boolean isActiveHelp() {
		return activeHelp;
	}

	public boolean isSwapSides() {
		return swapSides;
	}

	protected void onMove(BoardMove move) {
		logToOut("[MOVE] " + move);
	}

	@Override
	protected void onStopGame(StopReason reason) {
		super.onStopGame(reason);
	}

	@Override
	protected void open(Container server, Lobby lobby, int number, String variant, boolean allowWatchers, int[] options, Connection... players) throws IOException {
		super.open(server, lobby, number, variant, allowWatchers, options, players);

		firstGame = true;

		BoardContainer boardServer = (BoardContainer) server;

		minRating = boardServer.getMinRating();
		maxRating = boardServer.getMaxRating();

		whosOfferingDraw = null;
		whosSuggestedUndoMove = null;

		drawGameQuestion = new SeatQuestion();
	}

	@Override
	protected void parseGameData(Connection user, String opcode, GZStruct request) throws Throwable {
		switch (opcode) {
			case "cj": { // confirm move
				BoardGame game = getGame();
				if (!game.isRunning())
					break;

				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (seat.getPlayer().isMyTurn())
					break;

				if (game.hasMoves()) {
					getContainer().logToErr(toString(), "There are remaining moves yet");
					dumpGameState();
					user.postClose();
					break;
				}

				game.nextTurn();

				break;
			}

			case "mvt": { // move
				String s = request.getString("s");
				String[] s1 = s.split(":");

				int j = 0;
				int[] moves = new int[2 * s1.length];
				for (int i = 0; i < s1.length; i++) {
					int m = Integer.parseInt(s1[i]);
					moves[j++] = m / 10 - 1;
					moves[j++] = m % 10 - 1;
				}

				BoardGame game = getGame();
				if (!game.isRunning()) {
					user.postClose();
					break;
				}

				Seat seat = getSeat(user);
				if (seat == null) {
					user.postClose();
					break;
				}

				if (!seat.getPlayer().isMyTurn()) {
					user.postClose();
					break;
				}

				lastMoveUser = user;
				lastMove = s;

				BoardMove move = new BoardMove(moves);
				if (!game.doMove(move, false, false)) {
					getContainer().logToErr(toString(), "Invalid move " + move);
					dumpGameState();
					user.postClose();
					break;
				}

				onMove(move);

				for (Connection connection : connections) {
					if (connection == user)
						continue;

					GZStruct response = new GZStruct();
					GZStruct gd = new GZStruct();
					gd.setString("mvs", lastMove);
					response.setStruct("gd", gd);
					connection.postBlock(response);
				}

				break;
			}

			case "idraw": {
				BoardGame game = getGame();
				if (!game.isRunning())
					break;

				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (!seat.getPlayer().isMyTurn())
					break;

				lastMoveUser = user;
				drawOffering = true;

				whosOfferingDraw = seat;
				game.offerDraw();

				break;
			}

			case "adraw": {
				BoardGame game = getGame();
				if (!game.isRunning())
					break;

				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (seat.equals(whosOfferingDraw))
					break;

				whosOfferingDraw = null;
				game.acceptDraw();

				break;
			}

			case "rdraw": {
				BoardGame game = getGame();
				if (!game.isRunning())
					break;

				Seat seat = getSeat(user);
				if (seat == null)
					break;

				if (seat.equals(whosOfferingDraw))
					break;

				drawRejected = true;
				game.rejectDraw();

				break;
			}

			default:
				super.parseGameData(user, opcode, request);
		}
	}

	protected void suggestPauseGame(Seat seat, int time) {
		synchronized (this) {
			if (isClosing() || isClosed())
				return;
		}
	}

	protected boolean validateMaxRating(int maxRating) {
		BoardContainer container = getContainer();

		int min = container.getMinRating();
		int max = container.getMaxRating();

		if (maxRating < min || maxRating > max)
			return false;

		return true;
	}

	protected boolean validateMinRating(int minRating) {
		BoardContainer container = getContainer();

		int min = container.getMinRating();
		int max = container.getMaxRating();

		if (minRating < min || minRating > max)
			return false;

		return true;
	}

	protected boolean isReachedTheMinimumConditionalToRated() {
		return getGame().getHistoryCount() >= MINIMUM_MOVE_HISTORY_COUNT_TO_RATED;
	}

	protected int getTurnCount() {
		return getGame().getHistoryCount();
	}

}
