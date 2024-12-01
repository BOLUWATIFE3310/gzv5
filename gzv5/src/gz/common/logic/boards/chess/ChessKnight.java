package gz.common.logic.boards.chess;

import gz.common.logic.boards.BoardPosition;

public class ChessKnight extends ChessPiece {

	public ChessKnight(ChessGame game, int playerIndex) {
		super(game, playerIndex);
	}

	protected ChessKnight(ChessGame game, int playerIndex, BoardPosition position) {
		super(game, playerIndex, position);
	}

	@Override
	public Direction[] getDirs() {
		return new Direction[] { new Direction(2, -1), new Direction(1, -2), new Direction(-1, -2), new Direction(-2, -1), new Direction(-2, 1), new Direction(-1, 2), new Direction(1, 2),
				new Direction(2, 1) };
	}

	@Override
	public boolean isSlidingPiece() {
		return false;
	}

	@Override
	public String toString() {
		switch (getPlayerIndex()) {
			case 1:
				return "h";
			case 0:
				return "H";
		}

		return "?";
	}

}
