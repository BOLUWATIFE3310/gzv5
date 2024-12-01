package gz.common.logic.billiards.brazilian;

import java.util.ArrayList;

import gz.common.logic.billiards.BilliardsGame;
import gz.common.logic.billiards.BilliardsGameController;
import gz.common.logic.billiards.BilliardsPlayer;
import gz.common.logic.billiards.Point2D;

public class BrazilianPoolGame extends BilliardsGame {

	public enum BallType {
		WHITE, SMOOTH, STRAIGHT, BLACK
	}

	protected boolean initialState;
	protected boolean eightBallPotted;
	protected boolean myBallPotted;

	public BrazilianPoolGame(BilliardsGameController controller, int[] sqc) {
		super(controller, sqc);
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
	protected boolean beforeStart() {
		if (!super.beforeStart())
			return false;

		initialState = true;

		return true;
	}

	@Override
	protected void checkGame(BilliardsPlayer currentPlayer) {
		super.checkGame(currentPlayer);

		currentState.setHandBallMode(0);

		int[] myBallSet = currentPlayer.getBallSet();
		boolean imAtEight = myBallSet.length > 0 && myBallSet[0] == 8;
		BilliardsPlayer opponentPlayer = getPlayer(1 - currentPlayer.getIndex());

		if (eightBallPotted) {
			currentPlayer.removeBallFromBallSet(8);
			if (initialState || cueBallPotted || firstBallFault) {
				if (imAtEight) {
					int leastOpponentBallIndex = opponentPlayer.getLeastBallFromBallSet();
					opponentPlayer.removeBallFromBallSet(leastOpponentBallIndex);
					currentState.getBall(leastOpponentBallIndex).setVisible(false);

					if (opponentPlayer.getBallSet().length == 0) {
						gameOver = true;
						winner = opponentPlayer.getIndex();
					} else
						changeTurn = true;
				} else {
					gameOver = true;
					winner = opponentPlayer.getIndex();
				}
			} else if (!imAtEight) {
				gameOver = true;
				winner = opponentPlayer.getIndex();
			} else {
				gameOver = true;
				winner = currentPlayer.getIndex();
			}
		} else {
			if ((cueBallPotted || firstBallFault) && !initialState) {
				int leastOpponentBallIndex = opponentPlayer.getLeastBallFromBallSet();
				if (leastOpponentBallIndex == -1) {
					gameOver = true;
					winner = opponentPlayer.getIndex();
					return;
				}

				opponentPlayer.removeBallFromBallSet(leastOpponentBallIndex);
				opponentPlayer.addPottedBall(leastOpponentBallIndex);
				opponentPlayer.incrementScore();
				currentState.getBall(leastOpponentBallIndex).setVisible(false);

				if (opponentPlayer.getBallSet().length == 0 && leastOpponentBallIndex == 8) {
					gameOver = true;
					winner = opponentPlayer.getIndex();
					return;
				} else
					changeTurn = true;
			}

			if (changeTurn && !cueBallPotted && !firstBallFault && myBallPotted)
				changeTurn = false;

			if (initialState && firstBallPotted > 0) {
				initialState = false;
				BallType type = getBallType(firstBallPotted);

				currentPlayer.setBallSet(getInitialBallSet(firstBallPotted));
				currentPlayer.setBallIcon(type == BallType.SMOOTH ? 1 : 2);

				opponentPlayer.setBallSet(getInitialOtherBallSet(firstBallPotted));
				opponentPlayer.setBallIcon(type == BallType.SMOOTH ? 2 : 1);

				for (int i = 0; i < pottedBalls.size(); i++) {
					int ballIndex = pottedBalls.get(i);
					BallType type1 = getBallType(ballIndex);
					if (type == type1) {
						currentPlayer.addPottedBall(ballIndex);
						currentPlayer.incrementScore();
					} else if (type1 != BallType.WHITE && type1 != BallType.BLACK) {
						opponentPlayer.addPottedBall(ballIndex);
						opponentPlayer.incrementScore();
					}
				}
			}

			for (int i = 0; i < pottedBalls.size(); i++) {
				int ballIndex = pottedBalls.get(i);
				currentPlayer.removeBallFromBallSet(ballIndex);
				opponentPlayer.removeBallFromBallSet(ballIndex);
			}

			if (currentPlayer.getBallSet().length == 0) {
				if (currentState.getBall(8).isVisible())
					currentPlayer.setBallSet(new int[] { 8 });
				else {
					gameOver = true;
					winner = currentPlayer.getIndex();
					return;
				}
			}

			if (changeTurn && opponentPlayer.getBallSet().length == 0) {
				if (currentState.getBall(8).isVisible())
					opponentPlayer.setBallSet(new int[] { 8 });
				else {
					gameOver = true;
					winner = opponentPlayer.getIndex();
					return;
				}
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

		return null;
	}

	private int[] getInitialOtherBallSet(int ballIndex) {
		if (ballIndex >= 9 && ballIndex <= 15)
			return new int[] { 1, 2, 3, 4, 5, 6, 7 };

		if (ballIndex >= 1 && ballIndex <= 7)
			return new int[] { 9, 10, 11, 12, 13, 14, 15 };

		return null;
	}

	@Override
	protected void onPottedMyBall(BilliardsPlayer currentPlayer, int ballIndex) {
		if (!initialState) {
			currentPlayer.addPottedBall(ballIndex);
			currentPlayer.incrementScore();
		}
	}

	@Override
	protected void onPottedOtherBall(BilliardsPlayer currentPlayer, int ballIndex) {
		if (!initialState) {
			BilliardsPlayer opponentPlayer = getPlayer(1 - currentPlayer.getIndex());
			opponentPlayer.addPottedBall(ballIndex);
			opponentPlayer.incrementScore();
		}
	}

	@Override
	public void updateGameState(int cueBallIndex, int[] ballIndices, Point2D[] ballPositions, int[] pocketedBallIndices, int[] pocketedBallSlots, int[] hitBallIndices) {
		eightBallPotted = false;
		myBallPotted = false;

		super.updateGameState(cueBallIndex, ballIndices, ballPositions, pocketedBallIndices, pocketedBallSlots, hitBallIndices);
	}

}
