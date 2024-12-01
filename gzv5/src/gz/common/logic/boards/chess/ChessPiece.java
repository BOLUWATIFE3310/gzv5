package gz.common.logic.boards.chess;

import java.util.List;

import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPiece;
import gz.common.logic.boards.BoardPosition;

public abstract class ChessPiece extends BoardPiece {

	public ChessPiece(ChessGame game, int playerIndex) {
		super(game, playerIndex);
	}

	protected ChessPiece(ChessGame game, int playerIndex, BoardPosition position) {
		super(game, playerIndex, position);
	}

	@Override
	protected void destroy() {
		super.destroy();
	}

	@Override
	protected void generateMoveList(List<BoardMove> moveList) {
		ChessGame game = getGame();
		if (game == null)
			return;

		BoardPosition src = getPosition();
		if (src == null)
			return;

		int size = Math.max(game.getRowCount(), game.getRowCount());
		Direction[] dirs = getDirs();
		for (Direction dir : dirs)
			if (isSlidingPiece())
				for (int factor = 1; factor < size; factor++) {
					BoardPosition dst = dir.destination(src, factor);
					if (!game.isValidPos(dst))
						break;

					BoardMove move = new BoardMove(src, dst);

					ChessPiece other = game.getBoardInternal(dst);
					if (other == null)
						moveList.add(move);
					else if (isEnemy(other)) {
						moveList.add(move);
						break;
					} else if (isAllied(other))
						break;
				}
			else {
				BoardPosition dst = dir.destination(src);
				if (game.isValidPos(dst)) {
					ChessPiece other = game.getBoardInternal(dst);
					if (other == null || isEnemy(other))
						moveList.add(new BoardMove(src, dst));
				}
			}
	}

	public abstract Direction[] getDirs();

	@Override
	public ChessGame getGame() {
		return (ChessGame) super.getGame();
	}

	protected final boolean isAhead(int dstRow) {
		if (isWhite())
			return dstRow < getPosition().getRow();
		if (isBlack())
			return dstRow > getPosition().getRow();

		return false;
	}

	public final boolean isAllied(ChessPiece other) {
		return other != null && other.getPlayerIndex() == getPlayerIndex();
	}

	public final boolean isBlack() {
		return getPlayerIndex() == 1;
	}

	public final boolean isEnemy(ChessPiece other) {
		return other != null && other.getPlayerIndex() != getPlayerIndex();
	}

	public abstract boolean isSlidingPiece();

	public final boolean isWhite() {
		return getPlayerIndex() == 0;
	}

}
