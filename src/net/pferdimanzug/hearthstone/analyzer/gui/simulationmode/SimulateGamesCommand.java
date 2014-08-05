package net.pferdimanzug.hearthstone.analyzer.gui.simulationmode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import net.pferdimanzug.hearthstone.analyzer.GameNotification;
import net.pferdimanzug.hearthstone.analyzer.game.GameContext;
import net.pferdimanzug.hearthstone.analyzer.game.Player;
import net.pferdimanzug.hearthstone.analyzer.game.logic.GameLogic;
import net.pferdimanzug.hearthstone.analyzer.gui.gameconfig.GameConfig;
import net.pferdimanzug.hearthstone.analyzer.gui.gameconfig.PlayerConfig;
import net.pferdimanzug.hearthstone.analyzer.utils.Tuple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.pferdimanzug.nittygrittymvc.Notification;
import de.pferdimanzug.nittygrittymvc.SimpleCommand;
import de.pferdimanzug.nittygrittymvc.interfaces.INotification;

public class SimulateGamesCommand extends SimpleCommand<GameNotification> {

	private static Logger logger = LoggerFactory.getLogger(SimulateGamesCommand.class);

	private int gamesCompleted;
	private long lastUpdate;

	@Override
	public void execute(INotification<GameNotification> notification) {
		final GameConfig gameConfig = (GameConfig) notification.getBody();
		final SimulationResult result = new SimulationResult(gameConfig);

		gamesCompleted = 0;

		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				int poolSize = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
				//int poolSize = 1;
				logger.info("Starting simulation on {} cores", poolSize);
				ExecutorService executor = new ScheduledThreadPoolExecutor(poolSize);
				List<Future<GameContext>> tasks = new ArrayList<>(gameConfig.getNumberOfGames());

				// send initial status update
				Tuple<Integer, Integer> progress = new Tuple<>(0, gameConfig.getNumberOfGames());
				getFacade().sendNotification(GameNotification.SIMULATION_PROGRESS_UPDATE, progress);
				lastUpdate = System.currentTimeMillis();
				for (int i = 0; i < gameConfig.getNumberOfGames(); i++) {
					PlayGameTask task = new PlayGameTask(gameConfig);
					Future<GameContext> future = executor.submit(task);
					tasks.add(future);
				}

				executor.shutdown();
				
				while (!executor.isTerminated()) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}
				}
				
				for (Future<GameContext> future : tasks) {
					GameContext gameResult = null;
					try {
						gameResult = future.get();
					} catch (Exception e) {
						e.printStackTrace();
					} 
					result.getPlayer1Stats().merge(gameResult.getPlayer1().getStatistics());
					result.getPlayer2Stats().merge(gameResult.getPlayer2().getStatistics());
				}

				result.calculateMetaStatistics();
				getFacade().sendNotification(GameNotification.SIMULATION_RESULT, result);

			}
		});
		t.setDaemon(true);
		t.start();
	}

	private void onGameComplete(GameConfig gameConfig) {
		long timeStamp = System.currentTimeMillis();
		gamesCompleted++;
		if (timeStamp - lastUpdate > 100) {
			lastUpdate = timeStamp;
			Tuple<Integer, Integer> progress = new Tuple<>(gamesCompleted, gameConfig.getNumberOfGames());
			Notification<GameNotification> updateNotification = new Notification<>(GameNotification.SIMULATION_PROGRESS_UPDATE, progress);
			getFacade().notifyObservers(updateNotification);
		}
		
		
		
	}

	private class PlayGameTask implements Callable<GameContext> {

		private final GameConfig gameConfig;

		public PlayGameTask(GameConfig gameConfig) {
			this.gameConfig = gameConfig;
		}

		@Override
		public GameContext call() throws Exception {
			PlayerConfig playerConfig1 = gameConfig.getPlayerConfig1();
			PlayerConfig playerConfig2 = gameConfig.getPlayerConfig2();

			Player player1 = new Player("Player 1", playerConfig1.getHero().clone(), playerConfig1.getDeck());
			player1.setBehaviour(playerConfig1.getBehaviour().clone());

			Player player2 = new Player("Player 2", playerConfig2.getHero().clone(), playerConfig2.getDeck());
			player2.setBehaviour(playerConfig2.getBehaviour().clone());

			GameContext newGame = new GameContext(player1, player2, new GameLogic());
			newGame.play();

			onGameComplete(gameConfig);
			newGame.dispose();
			return newGame;
		}

	}

}