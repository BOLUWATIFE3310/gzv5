package gz.common.logic.boards.chess;

import gz.common.logic.boards.BoardPosition;

public class ChessKing extends ChessPiece {

	boolean moved;
	boolean castled;

	public ChessKing(ChessGame game, int playerIndex) {
		super(game, playerIndex);
	}

	protected ChessKing(ChessGame game, int playerIndex, BoardPosition position) {
		super(game, playerIndex, position);
	}

	@Override
	public Direction[] getDirs() {
		return new Direction[] { new Direction(1, 1), new Direction(1, -1), new Direction(-1, -1), new Direction(-1, 1), new Direction(1, 0), new Direction(0, 1), new Direction(-1, 0),
				new Direction(0, -1) };
	}

	@Override
	public boolean isSlidingPiece() {
		return false;
	}

	@Override
	public String toString() {
		switch (getPlayerIndex()) {
			case 1:
				return "k";
			case 0:
				return "K";
		}

		return "?";
	}

}
