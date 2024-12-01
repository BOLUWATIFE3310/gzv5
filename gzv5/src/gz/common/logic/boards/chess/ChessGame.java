package gz.common.logic.boards.chess;

import java.util.ArrayList;
import java.util.List;

import gz.common.logic.Player;
import gz.common.logic.boards.BoardGame;
import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPosition;

public class ChessGame extends BoardGame {

	public static final int WHITE = 0;
	public static final int BLACK = 1;

	private ChessPlayer[] players;

	boolean check;

	ChessKing[] kings;
	ChessTower[][] towers;

	public ChessGame(ChessGameController controller) {
		super(controller);

		players = (ChessPlayer[]) super.players;

		kings = new ChessKing[2];
		towers = new ChessTower[2][2];
	}

	@Override
	public boolean close() {
		if (!super.close())
			return false;

		kings[0] = null;
		kings[1] = null;
		towers[0][0] = null;
		towers[0][1] = null;
		towers[1][0] = null;
		towers[1][1] = null;

		return true;
	}

	@Override
	protected Player createPlayer(int playerIndex, String name) {
		return new ChessPlayer(this, playerIndex, name);
	}

	@Override
	protected Player[] createPlayers() {
		return new ChessPlayer[2];
	}

	private BoardPosition doMove(BoardPosition source, BoardPosition dest, ChessPiece[] capturedPiece) {
		int srcRow = source.getRow();
		int srcCol = source.getCol();
		int dstRow = dest.getRow();
		int dstCol = dest.getCol();

		ChessPiece piece = getBoardInternal(source);
		setBoardInternal(source, null);
		capturedPiece[0] = getBoardInternal(dest);
		BoardPosition captured = dest;
		setBoardInternal(dest, piece);

		if (piece instanceof ChessPawn) {
			ChessPawn pawn = (ChessPawn) piece;
			pawn.moved = true;
			// en passant
			if (srcCol != dstCol && capturedPiece[0] == null && (pawn.isBlack() && srcRow == 4 && dstRow == 5 || pawn.isWhite() && srcRow == 3 && dstRow == 2)) {
				captured = new BoardPosition(srcRow, dstCol);
				capturedPiece[0] = getBoardInternal(captured);
				setBoardInternal(srcRow, dstCol, null);
				assert capturedPiece != null;
			}
		} else if (piece instanceof ChessKing) {
			ChessKing king = (ChessKing) piece;
			king.moved = true;
			boolean castled = Math.abs(dstCol - srcCol) == 2;
			// roque
			if (!king.castled && castled) {
				king.castled = true;
				if (dstCol < srcCol) { // roque maior
					piece = getBoardInternal(srcRow, 0);
					setBoardInternal(srcRow, srcCol - 1, piece);
				} else { // roque menor
					piece = getBoardInternal(srcRow, 7);
					setBoardInternal(srcRow, srcCol + 1, piece);
				}
			}
		} else if (piece instanceof ChessTower) {
			ChessTower tower = (ChessTower) piece;
			tower.moved = true;
		}

		return captured;
	}

	@Override
	protected void doSingleMove(int srcRow, int srcCol, int dstRow, int dstCol, boolean hasMore) {
		ChessPiece[] capturedPiece = new ChessPiece[1];
		doMove(new BoardPosition(srcRow, srcCol), new BoardPosition(dstRow, dstCol), capturedPiece);
		if (capturedPiece[0] != null)
			capturedPiece[0].destroy();
	}

	@Override
	protected void generateMoveListImpl() {
		int myTurn = getCurrentTurn();
		ChessKing myKing = kings[myTurn];

		List<BoardMove> attackers = getAttackers();

		check = isChecking(myKing, attackers);

		super.generateMoveListImpl();

		if (check) {
			List<BoardMove> ml = new ArrayList<>(moveList);
			for (int i = 0; i < ml.size(); i++) {
				BoardMove move = ml.get(i);

				BoardPosition src = move.source();
				BoardPosition dst = move.dest();

				ChessPiece srcPiece = getBoardInternal(src);

				ChessPiece[] capturedPiece = new ChessPiece[1];
				BoardPosition captured = doMove(src, dst, capturedPiece);

				attackers = getAttackers();
				if (isChecking(myKing, attackers))
					moveList.remove(i);

				setBoardInternal(src, srcPiece);
				setBoardInternal(dst, null);
				setBoardInternal(captured, capturedPiece[0]);
			}
		} else if (!myKing.moved && !myKing.castled) {
			List<BoardMove> ml = new ArrayList<>(moveList);
			for (int i = 0; i < ml.size(); i++) {
				BoardMove move = ml.get(i);

				BoardPosition src = move.source();
				if (getBoardInternal(src) != myKing)
					continue;

				BoardPosition dst = move.dest();

				ChessPiece[] capturedPiece = new ChessPiece[1];
				BoardPosition captured = doMove(src, dst, capturedPiece);

				attackers = getAttackers();
				if (isChecking(myKing, attackers))
					moveList.remove(i);

				setBoardInternal(src, myKing);
				setBoardInternal(dst, null);
				setBoardInternal(captured, capturedPiece[0]);
			}

			ChessTower myTower = towers[myTurn][0]; // left tower
			BoardPosition source = myKing.getPosition();
			BoardPosition middle = Direction.LEFT.destination(source);
			BoardPosition dest = Direction.LEFT.destination(source, 2);
			if (!myTower.moved && isFreeRow(source.getRow(), 1, 3) && !isAttacking(middle, attackers) && !isAttacking(dest, attackers))
				moveList.add(new BoardMove(source, dest));
			myTower = towers[myTurn][1]; // right tower
			middle = Direction.RIGHT.destination(source);
			dest = Direction.RIGHT.destination(source, 2);
			if (!myTower.moved && isFreeRow(source.getRow(), 5, 6) && !isAttacking(middle, attackers) && !isAttacking(dest, attackers))
				moveList.add(new BoardMove(source, dest));
		}
	}

	private List<BoardMove> getAttackers() {
		List<BoardMove> result = new ArrayList<>();
		for (int row = 0; row < getRowCount(); row++)
			for (int col = 0; col < getColCount(); col++) {
				if (!isValidPos(row, col))
					continue;

				ChessPiece piece = getBoardInternal(row, col);
				if (piece == null)
					continue;

				if (piece.getPlayerIndex() == 1 - getCurrentTurn())
					piece.generateMoveList(result);
			}

		return result;
	}

	@Override
	protected ChessPiece getBoardInternal(BoardPosition position) {
		return (ChessPiece) super.getBoardInternal(position);
	}

	@Override
	protected ChessPiece getBoardInternal(int row, int col) {
		return (ChessPiece) super.getBoardInternal(row, col);
	}

	@Override
	public int getColCount() {
		return 8;
	}

	@Override
	public int getInitialTurn() {
		return 0;
	}

	@Override
	public int getMaxRounds() {
		return 0;
	}

	@Override
	public int getRowCount() {
		return 8;
	}

	private boolean isAttacking(BoardPosition position, List<BoardMove> attackers) {
		for (BoardMove move : attackers) {
			BoardPosition dst = move.dest();
			if (dst.equals(position))
				return true;
		}

		return false;
	}

	private boolean isChecking(ChessKing myKing, List<BoardMove> attackers) {
		return isAttacking(myKing.getPosition(), attackers);
	}

	private boolean isFreeRow(int row, int startCol, int endCol) {
		for (int col = startCol; col <= endCol; col++)
			if (getBoardInternal(row, col) != null)
				return false;

		return true;
	}

	protected void setBoardInternal(BoardPosition position, ChessPiece piece) {
		super.setBoardInternal(position, piece);
	}

	protected void setBoardInternal(int row, int col, ChessPiece piece) {
		super.setBoardInternal(row, col, piece);
	}

	@Override
	protected void setupBoard() {
		for (int row = 0; row < 8; row++)
			for (int col = 0; col < 8; col++) {
				ChessPiece piece = getBoardInternal(row, col);
				setBoardInternal(row, col, null);
				if (piece != null)
					piece.destroy();
			}

		setBoardInternal(0, 0, towers[BLACK][0] = new ChessTower(this, BLACK));
		setBoardInternal(0, 1, new ChessKnight(this, BLACK));
		setBoardInternal(0, 2, new ChessBishop(this, BLACK));
		setBoardInternal(0, 3, new ChessQueen(this, BLACK));
		setBoardInternal(0, 4, kings[BLACK] = new ChessKing(this, BLACK));
		setBoardInternal(0, 5, new ChessBishop(this, BLACK));
		setBoardInternal(0, 6, new ChessKnight(this, BLACK));
		setBoardInternal(0, 7, towers[BLACK][1] = new ChessTower(this, BLACK));
		setBoardInternal(1, 0, new ChessPawn(this, BLACK));
		setBoardInternal(1, 1, new ChessPawn(this, BLACK));
		setBoardInternal(1, 2, new ChessPawn(this, BLACK));
		setBoardInternal(1, 3, new ChessPawn(this, BLACK));
		setBoardInternal(1, 4, new ChessPawn(this, BLACK));
		setBoardInternal(1, 5, new ChessPawn(this, BLACK));
		setBoardInternal(1, 6, new ChessPawn(this, BLACK));
		setBoardInternal(1, 7, new ChessPawn(this, BLACK));

		setBoardInternal(6, 0, new ChessPawn(this, WHITE));
		setBoardInternal(6, 1, new ChessPawn(this, WHITE));
		setBoardInternal(6, 2, new ChessPawn(this, WHITE));
		setBoardInternal(6, 3, new ChessPawn(this, WHITE));
		setBoardInternal(6, 4, new ChessPawn(this, WHITE));
		setBoardInternal(6, 5, new ChessPawn(this, WHITE));
		setBoardInternal(6, 6, new ChessPawn(this, WHITE));
		setBoardInternal(6, 7, new ChessPawn(this, WHITE));
		setBoardInternal(7, 0, towers[WHITE][0] = new ChessTower(this, WHITE));
		setBoardInternal(7, 1, new ChessKnight(this, WHITE));
		setBoardInternal(7, 2, new ChessBishop(this, WHITE));
		setBoardInternal(7, 3, new ChessQueen(this, WHITE));
		setBoardInternal(7, 4, kings[WHITE] = new ChessKing(this, WHITE));
		setBoardInternal(7, 5, new ChessBishop(this, WHITE));
		setBoardInternal(7, 6, new ChessKnight(this, WHITE));
		setBoardInternal(7, 7, towers[WHITE][1] = new ChessTower(this, WHITE));
	}

	@Override
	public void stop(StopReason reason) {
		if (!isRunning())
			return;

		if (reason != StopReason.CANCELED)
			if (reason == StopReason.RESIGN)
				for (ChessPlayer player : players)
					if (!player.isPlaying())
						player.reportLoser();
					else
						player.reportWinner();
			else if (reason == StopReason.DRAW)
				for (ChessPlayer player : players)
					player.reportDraw();
			else if (check)
				for (ChessPlayer player : players)
					if (player.isMyTurn())
						player.reportLoser();
					else
						player.reportWinner();
			else
				for (ChessPlayer player : players)
					player.reportDraw();

		super.stop(reason);
	}

	@Override
	protected String turnToStr(int currentTurn) {
		switch (currentTurn) {
			case WHITE:
				return "White";
			case BLACK:
				return "Black";
		}

		return "?";
	}

}
