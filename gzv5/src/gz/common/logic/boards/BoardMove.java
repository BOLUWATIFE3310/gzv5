package gz.common.logic.boards;

import java.util.Arrays;
import java.util.Collection;

public class BoardMove {

	private BoardPosition[] positions;

	public BoardMove(BoardMove move) {
		this(move, 0, move.count());
	}

	public BoardMove(BoardMove move, int start, int count) {
		positions = new BoardPosition[count];

		for (int i = 0; i < count; i++)
			positions[i] = move.positions[i + start];
	}

	public BoardMove(BoardPosition... positions) {
		this.positions = new BoardPosition[positions.length];
		for (int i = 0; i < positions.length; i++)
			this.positions[i] = positions[i];
	}

	public BoardMove(Collection<? extends BoardPosition> c) {
		this(c.toArray(new BoardPosition[] {}));
	}

	public BoardMove(int... values) {
		int len = values.length;
		if ((len & 1) != 0)
			throw new RuntimeException("The numbers of arguments shold be a pair number.");

		len = len >>> 1;
		positions = new BoardPosition[len];

		for (int i = 0; i < len; i++)
			positions[i] = new BoardPosition(values[2 * i], values[2 * i + 1]);
	}

	public int count() {
		return positions.length;
	}

	public BoardPosition dest() {
		if (positions.length == 0)
			return null;

		return positions[positions.length - 1];
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;

		if (obj == null)
			return false;

		if (!(obj instanceof BoardMove))
			return false;

		BoardMove other = (BoardMove) obj;
		if (!Arrays.equals(positions, other.positions))
			return false;

		return true;
	}

	public BoardPosition get(int index) {
		return positions[index];
	}

	@Override
	public int hashCode() {
		int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(positions);
		return result;
	}

	public int length() {
		if (positions.length <= 1)
			return 0;

		int result = 0;
		for (int i = 1; i < positions.length; i++)
			result += Math.max(positions[i].getRow() - positions[i - 1].getRow(), positions[i].getCol() - positions[i - 1].getCol());

		return result;
	}

	public BoardPosition source() {
		if (positions.length == 0)
			return null;

		return positions[0];
	}

	public boolean startWith(BoardMove move) {
		if (move.positions.length > positions.length)
			return false;

		int min = Math.min(positions.length, move.positions.length);

		for (int i = 0; i < min; i++)
			if (!positions[i].equals(move.positions[i]))
				return false;

		return true;
	}

	public String toBoard2DNotation() {
		if (positions.length == 0)
			return "";

		String result = positions[0].toBoard2DNotation();
		for (int i = 1; i < positions.length; i++)
			result += " - " + positions[i].toBoard2DNotation();

		return result;
	}

	public String toEnglishNotation(int rowCount, int colCount) {
		if (positions.length == 0)
			return "";

		String result = positions[0].toEnglishNotation(rowCount, colCount);
		for (int i = 1; i < positions.length; i++)
			result += " - " + positions[i].toEnglishNotation(rowCount, colCount);

		return result;
	}

	@Override
	public String toString() {
		return "<" + toBoard2DNotation() + ">";
	}

}
