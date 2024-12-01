package gz.common.logic.boards.draughts;

import java.util.List;

import common.util.Tree;
import common.util.Tree.Node;
import gz.common.logic.boards.BoardMove;
import gz.common.logic.boards.BoardPiece;
import gz.common.logic.boards.BoardPosition;
import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public abstract class DraughtsPiece extends BoardPiece {

	private static boolean isValidMove(DraughtsGame game, DraughtsPiece piece, int srcRow, int srcCol, int dr, int dc) {
		if (!game.isValidPos(srcRow + dr, srcCol + dc))
			return false;

		if (game.getBoardInternal(srcRow + dr, srcCol + dc) != null)
			return false;

		if (piece.isKing())
			return true;

		if (piece.isBlack())
			return dr < 0;

		return dr > 0;
	}

	protected static int signal(int x) {
		if (x > 0)
			return 1;

		if (x < 0)
			return -1;

		return 0;
	}

	boolean captured;

	protected DraughtsPiece(DraughtsGame game, int playerIndex) {
		super(game, playerIndex);
	}

	protected DraughtsPiece(DraughtsGame game, int playerIndex, BoardPosition position) {
		super(game, playerIndex, position);
	}

	@Override
	protected void destroy() {
		super.destroy();
	}

	protected abstract void generateCaptureList(int dr, int dc, Node<DraughtsNodePosition> node);

	@Override
	protected final void generateMoveList(List<BoardMove> moveList) {
		DraughtsGame game = getGame();

		Tree<DraughtsNodePosition> move = new Tree<>(new DraughtsNodePosition(getPosition(), isKing()));
		Node<DraughtsNodePosition> root = move.getRoot();
		generateCaptureList(0, 0, root);

		if (move.getChildCount() > 0) {
			if (!game.hasCaptures) {
				moveList.clear();
				game.hasCaptures = true;
			}
			
			game.addMovesFromTree(move);
		}

		if (!game.hasCaptures)
			generateNonCaptureList(moveList);
	}

	protected abstract void generateNonCaptureList(List<BoardMove> moveList);

	@Override
	public DraughtsGame getGame() {
		return (DraughtsGame) super.getGame();
	}

	protected final boolean isAhead(int dstRow) {
		if (isWhite())
			return dstRow > getPosition().getRow();

		if (isBlack())
			return dstRow < getPosition().getRow();

		return false;
	}

	public final boolean isBlack() {
		return getPlayerIndex() == 1;
	}

	public final boolean isBlackKing() {
		return isKing() && isBlack();
	}

	public final boolean isBlackMan() {
		return isMan() && isBlack();
	}

	public abstract boolean isKing();

	public final boolean isMan() {
		return !isKing();
	}

	public final boolean isSystemBlocked() {
		DraughtsGame game = getGame();
		if (game == null)
			return false;

		BoardPosition position = getPosition();
		if (!game.isValidPos(position))
			return false;

		int row = position.getRow();
		int col = position.getCol();

		game.setBoardInternal(row, col, null);
		try {
			for (int dr = -1; dr <= 1; dr += 2)
				for (int dc = -1; dc <= 1; dc += 2)
					if (isValidMove(game, this, row, col, dr, dc)) {
						int row1 = row + dr;
						int col1 = col + dc;
						for (int dr1 = -1; dr1 <= 1; dr1 += 2)
							for (int dc1 = -1; dc1 <= 1; dc1 += 2) {
								int row2 = row1 + dr1;
								int col2 = col1 + dc1;

								if (row1 == row2 && col1 == col2)
									continue;

								if (!game.isValidPos(row2, col2))
									continue;

								DraughtsPiece other = game.getBoardInternal(row2, col2);
								if (other == null)
									continue;

								if (other.getPlayerIndex() == getPlayerIndex())
									continue;

								if (isValidMove(game, other, row2, col2, -2 * dr1, -2 * dc1))
									return true;
							}

						return false;
					}

			return true;
		} finally {
			game.setBoardInternal(row, col, this);
		}
	}

	protected abstract BoardPosition isValidSingleCapture(int dstRow, int dstCol, boolean firstCapture);

	public final boolean isWhite() {
		return getPlayerIndex() == 0;
	}

	public final boolean isWhiteKing() {
		return isKing() && isWhite();
	}

	public final boolean isWhiteMan() {
		return isMan() && isWhite();
	}

	protected void release() {
		getGame().release(this);
	}

}
