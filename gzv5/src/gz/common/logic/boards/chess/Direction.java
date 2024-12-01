package gz.common.logic.boards.chess;

import gz.common.logic.boards.BoardPosition;

public class Direction {

	public static final Direction NULL_DIRECTION = new Direction(0, 0);
	public static final Direction RIGHT = new Direction(0, 1);
	public static final Direction TOP = new Direction(-1, 0);
	public static final Direction LEFT = new Direction(0, -1);
	public static final Direction BOTTOM = new Direction(1, 0);

	private int deltaRow;
	private int deltaCol;

	public Direction(int deltaRow, int deltaCol) {
		this.deltaRow = deltaRow;
		this.deltaCol = deltaCol;
	}

	public BoardPosition destination(BoardPosition source) {
		return destination(source, 1);
	}

	public BoardPosition destination(BoardPosition source, int factor) {
		return new BoardPosition(source.getRow() + factor * deltaRow, source.getCol() + factor * deltaCol);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Direction))
			return false;
		Direction other = (Direction) obj;
		if (deltaRow != other.deltaRow)
			return false;
		if (deltaCol != other.deltaCol)
			return false;
		return true;
	}

	public int getDeltaCol() {
		return deltaCol;
	}

	public int getDeltaRow() {
		return deltaRow;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + deltaRow;
		result = prime * result + deltaCol;
		return result;
	}

	public Direction identity() {
		return new Direction(deltaRow, deltaCol);
	}

	public Direction mirrorCol() {
		return new Direction(deltaRow, -deltaCol);
	}

	public Direction mirrorRow() {
		return new Direction(-deltaRow, deltaCol);
	}

	public Direction rotate180() {
		return new Direction(-deltaRow, -deltaCol);
	}

	public Direction rotate90() {
		return new Direction(-deltaCol, deltaRow);
	}

	public Direction scale(int factor) {
		return new Direction(deltaRow * factor, deltaCol * factor);
	}

	@Override
	public String toString() {
		return "Direction [dx=" + deltaRow + ", dy=" + deltaCol + "]";
	}

	public Direction unitary() {
		return new Direction(deltaRow > 0 ? 1 : deltaRow < 0 ? -1 : 0, deltaCol > 0 ? 1 : deltaCol < 0 ? -1 : 0);
	}

}
