package gz.common.logic.boards.draughts;

import java.util.List;

import common.process.ProcessQueue;
import common.util.Tree;
import common.util.Tree.Node;
import gz.common.logic.Player;
import gz.common.logic.boards.BoardGame;
import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPosition;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public final class DraughtsGame extends BoardGame {

	@SuppressWarnings("null")
	public static final void main(String... args) {
		final String PLAYER1 = "Jheany";
		final String PLAYER2 = "K U R O S Λ K I";
		final String positionStr = "                                              b                       W   w   w             w     W       B     B               ";
		final int CURRENT_TURN = 1;
		final BoardMove MOVE = new BoardMove(0, 0, 2, 2, 4, 4);

		int[][] position = parsePosition(positionStr);

		DraughtsGame game = new DraughtsGame(new DraughtsGameController() {

			@Override
			public ProcessQueue getQueue() {
				return null;
			}

			@Override
			public void onChangeTurn(int turn) {

			}

			@Override
			public void onNextRound(int round) {

			}

			@Override
			public void onPause(int time) {

			}

			@Override
			public void onPauseTimeOut() {

			}

			@Override
			public void onResume() {

			}

			@Override
			public void onRotateLeft() {

			}

			@Override
			public void onRotateRight() {

			}

			@Override
			public void onStart() {

			}

			@Override
			public void onStarted() {

			}

			@Override
			public void onStop(StopReason reason) {

			}

			@Override
			public void onSwap(int index1, int index2) {

			}

			@Override
			public void onUndoLastMove() {

			}
		});

		try {
			game.setConfig(DraughtsConfig.GAMEZERV5);

			Player player1 = game.join(0, PLAYER1);
			Player player2 = game.join(1, PLAYER2);
			player1.start();
			player2.start();
			if (!game.start()) {
				System.err.println("Game not started!");
				return;
			}

			System.out.println("Game started!");

			game.setupPosition(position, CURRENT_TURN);
			System.out.println(game.toString());

			if (MOVE != null)
				if (!game.doMove(MOVE)) {
					System.err.println();
					System.err.println("Invalid move!");
				} else {
					System.out.println("Valid move!");

					if (!game.isRunning())
						System.out.println("Game Over!");
				}
		} finally {
			game.close();
		}
	}

	private static int[][] parsePosition(String src) {
		int[][] result = new int[8][8];

		int pos = 0;
		for (int row = 7; row >= 0; row--)
			for (int col = 0; col < 8; col++) {
				char piece = src.charAt(pos);
				pos += 2;

				switch (piece) {
					case ' ':
						result[col][row] = 0;
						break;

					case 'w':
						result[col][row] = 1;
						break;

					case 'b':
						result[col][row] = 2;
						break;

					case 'W':
						result[col][row] = 3;
						break;

					case 'B':
						result[col][row] = 4;
						break;
				}
			}

		return result;
	}

	private DraughtsConfig config;

	private DraughtsPlayer[] players;

	private int whiteManIndex;

	private DraughtsMan[] whiteMen;
	private int whiteKingIndex;
	private DraughtsKing[] whiteKings;
	private int blackManIndex;
	private DraughtsMan[] redMen;
	private int blackKingIndex;
	private DraughtsKing[] redKings;

	boolean hasCaptures;
	int maxCaptureds;
	int maxCapturedKings;
	boolean capturingWithKing;
	boolean startedCapturingAKing;

	private int movesWithoutCapturingOrPromotion;
	private boolean resetMoveCounter;

	public DraughtsGame(DraughtsGameController controller) {
		super(controller);

		config = DraughtsConfig.BRAZILIAN;

		players = (DraughtsPlayer[]) super.players;

		int size = getSize();
		int pieceCount = size * (size - 2) / 4;

		whiteMen = new DraughtsMan[pieceCount];
		whiteKings = new DraughtsKing[pieceCount];
		for (int i = 0; i < whiteMen.length; i++) {
			whiteMen[i] = new DraughtsMan(this, 0);
			whiteKings[i] = new DraughtsKing(this, 0);
		}

		redMen = new DraughtsMan[pieceCount];
		redKings = new DraughtsKing[pieceCount];
		for (int i = 0; i < redMen.length; i++) {
			redMen[i] = new DraughtsMan(this, 1);
			redKings[i] = new DraughtsKing(this, 1);
		}
	}

	DraughtsKing acquireKing(int playerIndex) {
		if (playerIndex == 0)
			return whiteKings[whiteKingIndex++];

		return redKings[blackKingIndex++];
	}

	DraughtsMan acquireMan(int playerIndex) {
		if (playerIndex == 0)
			return whiteMen[whiteManIndex++];

		return redMen[blackManIndex++];
	}

	private void addToMoveList(Node<DraughtsNodePosition> node) {
		List<DraughtsNodePosition> positions = node.getPathFromRoot();
		BoardPosition[] p = new BoardPosition[positions.size()];
		for (int i = 0; i < positions.size(); i++)
			p[i] = positions.get(i).position;

		BoardMove move = new BoardMove(p);
		moveList.add(move);
	}

	void addMovesFromTree(Tree<DraughtsNodePosition> tree) {
		for (Node<DraughtsNodePosition> node : tree) {
			DraughtsNodePosition dp = node.getValue();

			if (hasMaximumCapture()) {
				if (dp.captureds > maxCaptureds) {
					maxCaptureds = dp.captureds;
					capturingWithKing = dp.capturingWithKing;
					maxCapturedKings = dp.capturedKings;
					startedCapturingAKing = dp.startedCapturingAKing;
					moveList.clear();
				} else if (dp.captureds < maxCaptureds)
					continue;
			}

			if (hasMaximumCaptureWithKings()) {
				if (!capturingWithKing && dp.capturingWithKing) {
					capturingWithKing = true;
					maxCapturedKings = dp.capturedKings;
					startedCapturingAKing = dp.startedCapturingAKing;
					moveList.clear();
				} else if (capturingWithKing && !dp.capturingWithKing)
					continue;
			}

			if (hasMaximumCaptureAmountOfKings()) {
				if (dp.capturedKings > maxCapturedKings) {
					capturingWithKing = dp.capturingWithKing;
					maxCapturedKings = dp.capturedKings;
					startedCapturingAKing = dp.startedCapturingAKing;
					moveList.clear();
				} else if (dp.capturedKings < maxCapturedKings)
					continue;
			}

			if (hasMaximumCaptureAmountOfKingsStartingCapturingAKing()) {
				if (!startedCapturingAKing && dp.startedCapturingAKing) {
					capturingWithKing = dp.capturingWithKing;
					maxCapturedKings = dp.capturedKings;
					startedCapturingAKing = true;
					moveList.clear();
				} else if (startedCapturingAKing && !dp.startedCapturingAKing)
					continue;
			}

			addToMoveList(node);
		}
	}

	public boolean canManAvoidBackCaptures() {
		return config.canManAvoidBackCaptures();
	}

	public boolean canManCaptureKings() {
		return config.canManCaptureKings();
	}

	public boolean canManMakeBackCaptures() {
		return config.canManMakeBackCaptures();
	}

	@Override
	public boolean close() {
		if (!super.close())
			return false;

		for (int i = 0; i < redMen.length; i++) {
			if (redMen[i] != null) {
				redMen[i].destroy();
				redMen[i] = null;
			}

			if (redKings[i] != null) {
				redKings[i].destroy();
				redKings[i] = null;
			}

			if (whiteMen[i] != null) {
				whiteMen[i].destroy();
				whiteMen[i] = null;
			}

			if (whiteKings[i] != null) {
				whiteKings[i].destroy();
				whiteKings[i] = null;
			}
		}

		return true;
	}

	@Override
	protected Player createPlayer(int playerIndex, String name) {
		return new DraughtsPlayer(this, playerIndex, name);
	}

	@Override
	protected Player[] createPlayers() {
		return new DraughtsPlayer[2];
	}

	@Override
	protected void doSingleMove(int srcRow, int srcCol, int dstRow, int dstCol, boolean hasMore) {
		DraughtsPiece piece = getBoardInternal(srcRow, srcCol);
		setBoardInternal(srcRow, srcCol, null);
		setBoardInternal(dstRow, dstCol, piece);
		if (piece instanceof DraughtsMan) {
			DraughtsMan man = (DraughtsMan) piece;
			if (man.gotLastRow())
				if (hasMore) {
					if (manCanBecomeKingDuringCapture()) {
						resetMoveCounter = true;
						setBoardInternal(dstRow, dstCol, man.promote());
					}
				} else {
					resetMoveCounter = true;
					setBoardInternal(dstRow, dstCol, man.promote());
				}
		}

		for (int i = 1; i < Math.abs(dstRow - srcRow); i++) {
			int capturedRow = dstRow > srcRow ? srcRow + i : srcRow - i;
			int capturedCol = dstCol > srcCol ? srcCol + i : srcCol - i;
			DraughtsPiece captured = getBoardInternal(capturedRow, capturedCol);
			if (captured != null)
				resetMoveCounter = true;

			setBoardInternal(capturedRow, capturedCol, null);
		}
	}

	@Override
	protected void generateMoveListImpl() {
		hasCaptures = false;
		maxCaptureds = 0;
		maxCapturedKings = 0;
		capturingWithKing = false;
		startedCapturingAKing = false;

		super.generateMoveListImpl();

		if (!hasCaptures && hasSystemBlocks() && isSystemBlock())
			moveList.clear();
	}

	@Override
	protected DraughtsPiece getBoardInternal(BoardPosition position) {
		return (DraughtsPiece) super.getBoardInternal(position);
	}

	@Override
	protected DraughtsPiece getBoardInternal(int row, int col) {
		return (DraughtsPiece) super.getBoardInternal(row, col);
	}

	@Override
	public final int getColCount() {
		return getSize();
	}

	public DraughtsConfig getConfig() {
		return config;
	}

	public int getInitialColor() {
		return config.getInitialColor();
	}

	@Override
	public final int getInitialTurn() {
		return config.getInitialColor();
	}

	@Override
	public int getMaxRounds() {
		return 0;
	}

	@Override
	public DraughtsPlayer getPlayer(int playerIndex) {
		return (DraughtsPlayer) super.getPlayer(playerIndex);
	}

	@Override
	public final int getRowCount() {
		return getSize();
	}

	public int getSize() {
		return config.getSize();
	}

	public boolean hasFlyingKings() {
		return config.hasFlyingKings();
	}

	public boolean hasMaximumCapture() {
		return config.hasMaximumCapture();
	}

	public boolean hasMaximumCaptureWithKings() {
		return config.hasMaximumCaptureWithKings();
	}

	public boolean hasMaximumCaptureAmountOfKings() {
		return config.hasMaximumCaptureAmountOfKings();
	}

	public boolean hasMaximumCaptureAmountOfKingsStartingCapturingAKing() {
		return config.hasMaximumCaptureAmountOfKingsStartingCapturingAKing();
	}

	public boolean hasSystemBlocks() {
		return config.hasSystemBlocks();
	}

	public boolean isMirrored() {
		return config.isMirrored();
	}

	private boolean isSystemBlock() {
		int blockCount = 0;
		int pieceCount = 0;
		for (int row = 0; row < getRowCount(); row++)
			for (int col = 0; col < getColCount(); col++) {
				if (!isValidPos(row, col))
					continue;

				DraughtsPiece piece = getBoardInternal(row, col);
				if (piece == null)
					continue;

				if (piece.getPlayerIndex() != getCurrentTurn())
					continue;

				pieceCount++;

				if (piece.isSystemBlocked())
					blockCount++;
			}

		return blockCount == pieceCount;
	}

	@Override
	public boolean isValidPos(int row, int col) {
		return super.isValidPos(row, col) && (isMirrored() ? (row + col & 1) != 0 : (row + col & 1) == 0);
	}

	public boolean manCanBecomeKingDuringCapture() {
		return config.canManCanBecomeKingDuringCapture();
	}

	public boolean manDontStarBackCaptures() {
		return config.manDontStartBackCaptures();
	}

	void release(DraughtsPiece piece) {
		if (piece.isWhiteMan())
			whiteManIndex--;
		else if (piece.isWhiteKing())
			whiteKingIndex--;
		else if (piece.isBlackMan())
			blackManIndex--;
		else if (piece.isBlackKing())
			blackKingIndex--;
	}

	protected void setBoardInternal(BoardPosition position, DraughtsPiece piece) {
		super.setBoardInternal(position, piece);
	}

	protected void setBoardInternal(int row, int col, DraughtsPiece piece) {
		super.setBoardInternal(row, col, piece);
	}

	public void setConfig(DraughtsConfig config) {
		this.config = config;
	}

	@Override
	protected void setupBoard() {
		blackManIndex = 0;
		blackKingIndex = 0;
		whiteManIndex = 0;
		whiteKingIndex = 0;

		for (int i = 0; i < redMen.length; i++) {
			redMen[i].setPosition(null);
			redKings[i].setPosition(null);
			whiteMen[i].setPosition(null);
			whiteKings[i].setPosition(null);
		}

		int size = getSize();
		for (int row = 0; row < size / 2 - 1; row++)
			for (int col = 0; col < size; col++) {
				if (!isValidPos(row, col))
					continue;

				setBoardInternal(row, col, acquireMan(0));
			}

		for (int row = size / 2 - 1; row < size / 2 + 1; row++)
			for (int col = 0; col < size; col++) {
				if (!isValidPos(row, col))
					continue;

				setBoardInternal(row, col, null);
			}

		for (int row = size / 2 + 1; row < size; row++)
			for (int col = 0; col < size; col++) {
				if (!isValidPos(row, col))
					continue;

				setBoardInternal(row, col, acquireMan(1));
			}
	}

	public void setupPosition(String position, int turn) {
		setupPosition(parsePosition(position), turn);
	}

	public void setupPosition(int[][] position, int turn) {
		blackManIndex = 0;
		blackKingIndex = 0;
		whiteManIndex = 0;
		whiteKingIndex = 0;

		for (int i = 0; i < redMen.length; i++) {
			redMen[i].setPosition(null);
			redKings[i].setPosition(null);
			whiteMen[i].setPosition(null);
			whiteKings[i].setPosition(null);
		}

		int size = getSize();
		for (int row = 0; row < size; row++)
			for (int col = 0; col < size; col++) {
				if (!isValidPos(row, col))
					continue;

				switch (position[col][row]) {
					case 1:
						setBoardInternal(row, col, acquireMan(0));
						break;

					case 2:
						setBoardInternal(row, col, acquireMan(1));
						break;

					case 3:
						setBoardInternal(row, col, acquireKing(0));
						break;

					case 4:
						setBoardInternal(row, col, acquireKing(1));
						break;
				}
			}

		setCurrentTurn(turn);
		generateMoveList();
	}

	public String toStateString() {
		String result = "";
		for (int row = 7; row >= 0; row--)
			for (int col = 0; col < 8; col++) {
				DraughtsPiece piece = getBoardInternal(row, col);

				if (piece == null)
					result += " ";
				else if (piece.isBlackMan())
					result += "b";
				else if (piece.isBlackKing())
					result += "B";
				else if (piece.isWhiteMan())
					result += "w";
				else if (piece.isWhiteKing())
					result += "W";

				result += " ";
			}

		return result;
	}

	@Override
	public void stop(StopReason reason) {
		if (!isRunning())
			return;

		if (reason != StopReason.CANCELED)
			if (reason == StopReason.RESIGN)
				for (DraughtsPlayer player : players)
					if (!player.isPlaying())
						player.reportLoser();
					else
						player.reportWinner();
			else if (reason == StopReason.DRAW)
				for (DraughtsPlayer player : players)
					player.reportDraw();
			else
				for (DraughtsPlayer player : players)
					if (player.isMyTurn())
						player.reportLoser();
					else
						player.reportWinner();

		super.stop(reason);
	}

	@Override
	protected String turnToStr(int currentTurn) {
		switch (currentTurn) {
			case DraughtsConfig.BLACK:
				return "Black";

			case DraughtsConfig.WHITE:
				return "White";
		}

		return "?";
	}

	public boolean stopCapturingAtLastRow() {
		return config.stopCapturingAtLastRow();
	}

	public int getNumberOfMovesWithoutCapturingOrPromotion() {
		return movesWithoutCapturingOrPromotion;
	}

	protected void afterStart() {
		super.afterStart();

		movesWithoutCapturingOrPromotion = 0;
		resetMoveCounter = false;
	}

	protected void afterNextTurn() {
		super.afterNextTurn();

		if (!isRunning())
			return;

		if (resetMoveCounter) {
			resetMoveCounter = false;
			movesWithoutCapturingOrPromotion = 0;
		} else
			movesWithoutCapturingOrPromotion++;

		if (hasAutoDraw() && movesWithoutCapturingOrPromotion >= config.getMinimalNumberMovesToDraw() && !hasCaptures)
			stop(StopReason.DRAW);
	}

}
