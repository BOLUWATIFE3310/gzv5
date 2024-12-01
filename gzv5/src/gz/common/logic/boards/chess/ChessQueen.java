package gz.common.logic.boards.chess;

import gz.common.logic.boards.BoardPosition;

public class ChessQueen extends ChessPiece {

	public ChessQueen(ChessGame game, int playerIndex) {
		super(game, playerIndex);
	}

	protected ChessQueen(ChessGame game, int playerIndex, BoardPosition position) {
		super(game, playerIndex, position);
	}

	@Override
	public Direction[] getDirs() {
		return new Direction[] { new Direction(1, 1), new Direction(1, -1), new Direction(-1, -1), new Direction(-1, 1), new Direction(1, 0), new Direction(0, 1), new Direction(-1, 0),
				new Direction(0, -1) };
	}

	@Override
	public boolean isSlidingPiece() {
		return true;
	}

	@Override
	public String toString() {
		switch (getPlayerIndex()) {
			case 1:
				return "q";
			case 0:
				return "Q";
		}

		return "?";
	}

}
