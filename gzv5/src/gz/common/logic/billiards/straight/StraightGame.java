package gz.common.logic.billiards.straight;

import java.util.ArrayList;

import gz.common.logic.billiards.BilliardsGame;
import gz.common.logic.billiards.BilliardsGameController;
import gz.common.logic.billiards.BilliardsPlayer;
import gz.common.logic.billiards.Point2D;

public class StraightGame extends BilliardsGame {

	private boolean gzpool;

	public StraightGame(BilliardsGameController controller, int[] sqc, boolean gzpool) {
		super(controller, sqc);

		this.gzpool = gzpool;
	}

	@Override
	protected void checkGame(BilliardsPlayer currentPlayer) {
		super.checkGame(currentPlayer);

		changeTurn = true;

		if (firstBallFault || cueBallPotted) {
			for (int i = 0; i < pottedBalls.size(); i++) {
				int ballIndex = pottedBalls.get(i);
				currentState.getBall(ballIndex).restoreAtRandomPosition();
			}
		} else if (pottedBalls.size() > 0) {
			changeTurn = false;

			for (int i = 0; i < pottedBalls.size(); i++) {
				int ballIndex = pottedBalls.get(i);
				currentPlayer.addPottedBall(ballIndex);
				currentPlayer.incrementScore();
			}
		}

		if (currentPlayer.getScore() >= 8) {
			gameOver = true;
			winner = currentPlayer.getIndex();
		}
	}

	@Override
	public int[] initialBallSet() {
		return null;
	}

	@Override
	public int initialHandBallMode() {
		return 2;
	}

	@Override
	protected void initializeBallPositions() {
		if (gzpool) {
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
			int[] __reg13 = new int[] { 1, 2, 3, 4, 5 };
			int __reg20 = 24000;
			int __reg16 = b_ct;
			int __reg11 = od + 100;
			int __reg15 = 1730;
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
		} else {
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
	}

	@Override
	protected void initializeOffsets() {
		if (gzpool) {
			fd_w = 70000;
			fd_h = 35000;
			x_offs = 5000;
			y_offs = 4000;
			bnm = 16;
			upk = 1;
			pw_cn = 3600;
			pw_sd = 4610;
			pdtf = 800;
			or = 1000;
			ei = 8;
			bi = 7;
			whsm = 5;
		} else {
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
	}

	public boolean isGZPool() {
		return gzpool;
	}

}
