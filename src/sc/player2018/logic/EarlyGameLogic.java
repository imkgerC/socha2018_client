/*
 * early-game logic trading speed for many more carrots and an inherent position advantage. 
 * Does not work well with mid-games involving staying behind, works best with a hybrid run/stay-mid-game, running if
 * you were able to stay behind, staying once if you needed to run first.
 */

package sc.player2018.logic;

import java.util.ArrayList;

import sc.plugin2018.CardType;
import sc.plugin2018.FieldType;
import sc.plugin2018.GameState;
import sc.plugin2018.Move;
import sc.plugin2018.Player;

public class EarlyGameLogic {
	static int SALAD_FIELD = 10;

	public static Move getTurn(GameState gameState) {
		Player currentPlayer = gameState.getCurrentPlayer();
		Player otherPlayer = gameState.getOtherPlayer();
		ArrayList<Move> possibleMoves = gameState.getPossibleMoves();
		MoveList baseList = new MoveList(possibleMoves, gameState);

		if (currentPlayer.getFieldIndex() < otherPlayer.getFieldIndex()) {
			if (gameState.getNextFieldByType(FieldType.HARE, currentPlayer.getFieldIndex()) < otherPlayer
					.getFieldIndex()) {
				Move selectedMove = baseList.select(CardType.EAT_SALAD).getNearest();
				if (selectedMove != null) {
					return selectedMove;
				}
			}

			if (SALAD_FIELD < otherPlayer.getFieldIndex()) {
				Move selectedMove = baseList.select(FieldType.SALAD).getNearest();
				if (selectedMove != null) {
					return selectedMove;
				}
			}

		}
		if (currentPlayer.getCarrots() > 125) { // random value oriented on KvC, calculate a better one
			if (!gameState.isOccupied(SALAD_FIELD)) {
				Move selectedMove = baseList.select(FieldType.SALAD).getNearest();
				if (selectedMove != null) {
					return selectedMove;
				}
			}
		}
		if (gameState.getTypeAt(currentPlayer.getFieldIndex()) == FieldType.CARROT) {
			Move selectedMove = baseList.getCarrotExchange(10);
			if (selectedMove != null) {
				return selectedMove;
			}
		}
		Move selectedMove = baseList.deselect(CardType.HURRY_AHEAD).deselect(CardType.EAT_SALAD)
				.deselect(CardType.TAKE_OR_DROP_CARROTS, -20).deselect(CardType.TAKE_OR_DROP_CARROTS, 0)
				.deselect(CardType.EAT_SALAD).getNearest();
		if (selectedMove != null) {
			return selectedMove;
		}

		return null;
	}
}
