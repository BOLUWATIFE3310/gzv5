package gz.common.logic.billiards.eightball;

import java.util.ArrayList;

import gz.common.logic.billiards.BilliardsGame;
import gz.common.logic.billiards.BilliardsGameController;
import gz.common.logic.billiards.BilliardsPlayer;
import gz.common.logic.billiards.Point2D;

public class EightBallGame extends BilliardsGame {

	public enum BallType {
		WHITE, SMOOTH, STRAIGHT, BLACK
	}

	private boolean eightBallPotted;

	private boolean myBallPotted;

	private boolean initialState;

	public EightBallGame(BilliardsGameController controller, int[] sqc) {
		super(controller, sqc);
	}

	@Override
	protected boolean beforeStart() {
		if (!super.beforeStart())
			return false;

		initialState = true;

		return true;
	}

	@Override
	protected void checkGame(BilliardsPlayer currentPlayer) {
		super.checkGame(currentPlayer);

		int[] myBallSet = currentPlayer.getBallSet();
		boolean imAtEight = myBallSet.length > 0 && myBallSet[0] == 8;
		
		BilliardsPlayer opponentPlayer = getPlayer(1 - currentPlayer.getIndex());

		if (eightBallPotted) {
			gameOver = true;
			if (initialState || cueBallPotted || firstBallFault || !imAtEight)
				winner = 1 - currentPlayer.getIndex();
			else
				winner = currentPlayer.getIndex();
		} else {
			if (firstBallFault || cueBallPotted) {
				changeTurn = true;
				
				if (cueBallPotted && firstBallHit == 8) {
					gameOver = true;
					winner = opponentPlayer.getIndex();
					return;
				}
				
				for (int i = 0; i < pottedBalls.size(); i++) {
					int ballIndex = pottedBalls.get(i);
					if (opponentPlayer.bellongsToMyBallSet(ballIndex)) {
						opponentPlayer.removeBallFromBallSet(ballIndex);
						opponentPlayer.addPottedBall(ballIndex);
						opponentPlayer.incrementScore();	
					} else if (currentPlayer.bellongsToMyBallSet(ballIndex))
						currentState.getBall(ballIndex).restoreAtRandomPosition();
				}
			}
			else {
				if (changeTurn && myBallPotted)
					changeTurn = false;
	
				BallType type;
				if (initialState && firstBallPotted > 0) {
					initialState = false;
					type = getBallType(firstBallPotted);
	
					currentPlayer.setBallSet(getInitialBallSet(firstBallPotted));
					currentPlayer.setBallIcon(type == BallType.SMOOTH ? 1 : 2);
	
					opponentPlayer.setBallSet(getInitialOtherBallSet(firstBallPotted));
					opponentPlayer.setBallIcon(type == BallType.SMOOTH ? 2 : 1);
				} else
					type = currentPlayer.getBallIcon() == 1 ? BallType.SMOOTH : BallType.STRAIGHT;
				
				for (int i = 0; i < pottedBalls.size(); i++) {
					int ballIndex = pottedBalls.get(i);

					if (currentPlayer.bellongsToMyBallSet(ballIndex)) {
						currentPlayer.removeBallFromBallSet(ballIndex);
						currentPlayer.addPottedBall(ballIndex);
						currentPlayer.incrementScore();
					} else if (opponentPlayer.bellongsToMyBallSet(ballIndex)) {
						opponentPlayer.removeBallFromBallSet(ballIndex);
						opponentPlayer.addPottedBall(ballIndex);
						opponentPlayer.incrementScore();
					}
				}
				
				if (currentPlayer.getBallSet().length == 0)
					currentPlayer.setBallSet(new int[] { 8 });

				if (opponentPlayer.getBallSet().length == 0)
					opponentPlayer.setBallSet(new int[] { 8 });
			}
		}
	}

	@Override
	@SuppressWarnings("unused")
	protected void checkPottedBalls(BilliardsPlayer currentPlayer, int[] pottedBallIndices, int[] pottedBallSlots) {
		super.checkPottedBalls(currentPlayer, pottedBallIndices, pottedBallSlots);

		for (int i = 0; i < pottedBallIndices.length; i++) {
			int ballIndex = pottedBallIndices[i];
			int slotIndex = pottedBallSlots[i];

			if (ballIndex == 8)
				eightBallPotted = true;
			else if (ballIndex != 0 && currentPlayer.bellongsToMyBallSet(ballIndex))
				myBallPotted = true;
		}
	}

	public BallType getBallType(int ballIndex) {
		if (ballIndex == 0)
			return BallType.WHITE;

		if (ballIndex == 8)
			return BallType.BLACK;

		if (1 <= ballIndex && ballIndex <= 7)
			return BallType.SMOOTH;

		if (9 <= ballIndex && ballIndex <= 15)
			return BallType.STRAIGHT;

		return null;
	}

	private int[] getInitialBallSet(int ballIndex) {
		if (ballIndex >= 1 && ballIndex <= 7)
			return new int[] { 1, 2, 3, 4, 5, 6, 7 };

		if (ballIndex >= 9 && ballIndex <= 15)
			return new int[] { 9, 10, 11, 12, 13, 14, 15 };

		return new int[] { 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15 };
	}

	private int[] getInitialOtherBallSet(int ballIndex) {
		if (ballIndex >= 9 && ballIndex <= 15)
			return new int[] { 1, 2, 3, 4, 5, 6, 7 };

		if (ballIndex >= 1 && ballIndex <= 7)
			return new int[] { 9, 10, 11, 12, 13, 14, 15 };

		return new int[] { 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15 };
	}

	@Override
	public int[] initialBallSet() {
		return new int[] { 1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 15 };
	}

	@Override
	public int initialHandBallMode() {
		return 2;
	}

	@Override
	protected void initializeBallPositions() {
		initialPositions[0] = new Point2D(58500, b_ct);

		ArrayList<Integer> __reg3 = new ArrayList<>();
		int __reg6 = 1;
		while (__reg6 < bnm) {
			if (sqc[__reg6] > 8)
				__reg3.add(__reg6);
			else
				__reg3.add(0, __reg6);
			++__reg6;
		}
		
		__reg6 = 1;
		while (__reg6 < bnm) {
			if (__reg6 < __reg3.size() && __reg3.get(__reg6) == 8) {
				__reg3.set(__reg6, __reg3.get(4));
				__reg3.set(4, 8);
				break;
			}
			++__reg6;
		}
		int[] __reg13 = new int[] { 1, 2, 3, 4, 5 };
		int __reg20 = 24000;
		int __reg16 = b_ct;
		int __reg11 = od + 100;
		int __reg15 = 1620;
		int __reg17 = __reg13.length;
		int __reg4 = 0;
		int __reg12 = __reg20;
		__reg6 = 0;
		while (__reg6 < __reg17) {
			int __reg9 = __reg13[__reg6];
			int __reg8 = __reg16 - __reg9 * __reg11 / 2 + or;
			int __reg5 = 1;
			while (__reg5 <= __reg9) {
				initialPositions[__reg3.get(__reg4)] = new Point2D(__reg12 + (sqc[__reg4] - 7) * 10, __reg8 + (sqc[31 - __reg4] - 7) * 10);
				__reg8 = __reg8 + __reg11;
				++__reg4;
				++__reg5;
			}
			__reg12 = __reg12 - __reg15;
			++__reg6;
		}
	}

	@Override
	protected void initializeOffsets() {
		bnm = 16;
		upk = 1;
		pw_cn = 3380;
		pw_sd = 4020;
		pdtf = 800;
		or = 920;
		ei = 8;
		bi = 7;
		whsm = 5;
	}

	@Override
	public void updateGameState(int cueBallIndex, int[] ballIndices, Point2D[] ballPositions, int[] pocketedBallIndices, int[] pocketedBallSlots, int[] hitBallIndices) {
		eightBallPotted = false;
		myBallPotted = false;

		super.updateGameState(cueBallIndex, ballIndices, ballPositions, pocketedBallIndices, pocketedBallSlots, hitBallIndices);
	}
	
	protected boolean resetCueBallWhenPotted() {
		return true;
	}

}
