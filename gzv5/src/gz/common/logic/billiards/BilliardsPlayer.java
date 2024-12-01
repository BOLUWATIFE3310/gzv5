package gz.common.logic.billiards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gz.common.logic.MultiplayerGame;
import gz.common.logic.Player;

public class BilliardsPlayer extends Player {

	int ballIcon;
	int score;
	int[] ballSet;
	ArrayList<Integer> pottedBalls;

	protected BilliardsPlayer(MultiplayerGame game, int index, String name) {
		super(game, index, name);

		ballIcon = -1;
		ballSet = null;

		pottedBalls = new ArrayList<>();
	}

	public void addPottedBall(int ballIndex) {
		pottedBalls.add(ballIndex);
	}

	public boolean bellongsToMyBallSet(int ballIndex) {
		if (ballSet == null)
			return true;

		for (int i = 0; i < ballSet.length; i++)
			if (ballSet[i] == ballIndex)
				return true;

		return false;
	}

	public void clearPottedBalls() {
		pottedBalls.clear();
	}

	public int getBallIcon() {
		return ballIcon;
	}

	public int[] getBallSet() {
		return ballSet;
	}

	public int getScore() {
		return score;
	}

	public int incrementScore() {
		score++;
		return score;
	}

	public int incrementScore(int delta) {
		score += delta;
		return score;
	}

	public List<Integer> pottedBalls() {
		return Collections.unmodifiableList(pottedBalls);
	}

	public void removeBallFromBallSet(int ballIndex) {
		ArrayList<Integer> ballSetList = new ArrayList<>();
		for (int i = 0; i < ballSet.length; i++)
			ballSetList.add(ballSet[i]);

		ballSetList.remove((Object) ballIndex);

		ballSet = new int[ballSetList.size()];
		for (int i = 0; i < ballSet.length; i++)
			ballSet[i] = ballSetList.get(i);
	}

	public void removePottedBall(int ballIndex) {
		pottedBalls.remove((Object) ballIndex);
	}

	@Override
	protected void reportLoser() {
		super.reportLoser();
	}

	@Override
	protected void reportWinner() {
		super.reportWinner();
	}

	public void setBallIcon(int index) {
		ballIcon = index;
	}

	public void setBallSet(int[] ballSet) {
		this.ballSet = ballSet;
	}

	public void setBallSet(List<Integer> ballSet) {
		this.ballSet = new int[ballSet.size()];
		for (int i = 0; i < ballSet.size(); i++)
			this.ballSet[i] = ballSet.get(i);
	}

	public void setScore(int value) {
		score = value;
	}

	public int getLeastBallFromBallSet() {
		if (ballSet == null)
			return 1;
		
		if (ballSet.length == 0)
			return -1;
		
		int result = ballSet[0];
		for (int i = 1; i < ballSet.length; i++) {
			int ballIndex = ballSet[i];
			if (ballIndex < result)
				result = ballIndex;
		}
		
		return result;
	}

}
