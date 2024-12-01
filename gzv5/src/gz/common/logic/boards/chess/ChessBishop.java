package gz.common.logic.boards.chess;

import gz.common.logic.boards.BoardPosition;

public class ChessBishop extends ChessPiece {

	public ChessBishop(ChessGame game, int playerIndex) {
		super(game, playerIndex);
	}

	protected ChessBishop(ChessGame game, int playerIndex, BoardPosition position) {
		super(game, playerIndex, position);
	}

	@Override
	public Direction[] getDirs() {
		return new Direction[] { new Direction(1, 1), new Direction(1, -1), new Direction(-1, -1), new Direction(-1, 1) };
	}

	@Override
	public boolean isSlidingPiece() {
		return true;
	}

	@Override
	public String toString() {
		switch (getPlayerIndex()) {
			case 1:
				return "b";
			case 0:
				return "B";
		}

		return "?";
	}

}
