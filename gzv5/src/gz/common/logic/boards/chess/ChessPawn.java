package gz.common.logic.boards.chess;

import java.util.ArrayList;
import java.util.List;

import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPosition;

public class ChessPawn extends ChessPiece {

	boolean moved;

	public ChessPawn(ChessGame game, int playerIndex) {
		super(game, playerIndex);
	}

	protected ChessPawn(ChessGame game, int playerIndex, BoardPosition position) {
		super(game, playerIndex, position);
	}

	@Override
	protected void generateMoveList(List<BoardMove> moveList) {
		super.generateMoveList(moveList);
	}

	@Override
	public Direction[] getDirs() {
		List<Direction> directions = new ArrayList<>();
		Direction moveDir;
		Direction captureDir;
		Direction enPassantDir;
		if (isWhite()) {
			moveDir = new Direction(-1, 0);
			captureDir = new Direction(-1, 1);
			enPassantDir = new Direction(0, 1);
		} else if (isBlack()) {
			moveDir = new Direction(1, 0);
			captureDir = new Direction(1, 1);
			enPassantDir = new Direction(0, 1);
		} else
			return new Direction[] {};

		ChessGame game = getGame();
		BoardPosition src = getPosition();
		BoardPosition dst = moveDir.destination(src);
		// movimento simples
		if (game.isValidPos(dst) && game.getBoardInternal(dst) == null) {
			directions.add(moveDir);
			Direction moveDir1 = moveDir.scale(2);
			dst = moveDir1.destination(src, 2);
			// movimento duplo
			if (!moved && game.isValidPos(dst) && game.getBoardInternal(dst) == null)
				directions.add(moveDir1);

			Direction dir1;
			Direction dir2;
			// checa por possíveis capturas
			for (dir1 = captureDir, dir2 = enPassantDir; !dir1.equals(captureDir); dir1 = dir1.mirrorCol(), dir2 = dir2.mirrorCol()) {
				BoardPosition dst1 = dir1.destination(src);
				BoardPosition dst2 = dir2.destination(src);
				if (game.isValidPos(dst1))
					if (game.getBoardInternal(dst1) != null) // captura
						directions.add(dir1);
					else { // verifica se é possível realizar o en passant
						BoardMove move = game.getLastMove();
						if (move != null && move.length() == 2 && game.getBoardInternal(move.dest()) instanceof ChessPawn && game.getBoardInternal(dst2) == null)
							directions.add(dir1);
					}
			}
		}

		return directions.toArray(new Direction[] {});
	}

	public boolean gotLastRow() {
		int dstRow = getPosition().getRow();
		if (isWhite() && dstRow == 0)
			return true;
		if (isBlack() && dstRow == getGame().getRowCount() - 1)
			return true;

		return false;
	}

	@Override
	public boolean isSlidingPiece() {
		return false;
	}

	@Override
	public String toString() {
		switch (getPlayerIndex()) {
			case 1:
				return "p";
			case 0:
				return "P";
		}

		return "?";
	}

}
