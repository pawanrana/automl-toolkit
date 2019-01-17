package com.databricks.spark.automatedml.model

import com.databricks.spark.automatedml.params.{Defaults, LinearRegressionConfig, LinearRegressionModelsWithResults}
import com.databricks.spark.automatedml.utils.SparkSessionWrapper
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.regression.LinearRegression
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.col

import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool
import org.apache.log4j.{Level, Logger}

import scala.collection.parallel.mutable.ParHashSet

class LinearRegressionTuner(df: DataFrame) extends SparkSessionWrapper with Defaults
  with Evolution {

  private val logger: Logger = Logger.getLogger(this.getClass)

  private var _scoringMetric = _scoringDefaultRegressor
  private var _linearRegressionNumericBoundaries = _linearRegressionDefaultNumBoundaries
  private var _linearRegressionStringBoundaries = _linearRegressionDefaultStringBoundaries

  def setScoringMetric(value: String): this.type = {
    require(regressionMetrics.contains(value),
      s"Regressor scoring metric '$value' is not a valid member of ${
        invalidateSelection(value, regressionMetrics)
      }")
    this._scoringMetric = value
    this
  }

  def setLinearRegressionNumericBoundaries(value: Map[String, (Double, Double)]): this.type = {
    this._linearRegressionNumericBoundaries = value
    this
  }

  def setLinearRegressionStringBoundaries(value: Map[String, List[String]]): this.type = {
    this._linearRegressionStringBoundaries = value
    this
  }

  def getScoringMetric: String = _scoringMetric

  def getLinearRegressionNumericBoundaries: Map[String, (Double, Double)] = _linearRegressionNumericBoundaries

  def getLinearRegressionStringBoundaries: Map[String, List[String]] = _linearRegressionStringBoundaries

  def getRegressionMetrics: List[String] = regressionMetrics

  private def configureModel(modelConfig: LinearRegressionConfig): LinearRegression = {
    new LinearRegression()
      .setLabelCol(_labelCol)
      .setFeaturesCol(_featureCol)
      .setElasticNetParam(modelConfig.elasticNetParams)
      .setFitIntercept(modelConfig.fitIntercept)
      .setLoss(modelConfig.loss)
      .setMaxIter(modelConfig.maxIter)
      .setRegParam(modelConfig.regParam)
      .setSolver("auto")
      .setStandardization(modelConfig.standardization)
      .setTol(modelConfig.tolerance)
  }

  private def returnBestHyperParameters(collection: ArrayBuffer[LinearRegressionModelsWithResults]):
  (LinearRegressionConfig, Double) = {

    val bestEntry = _optimizationStrategy match {
      case "minimize" => collection.result.toArray.sortWith(_.score < _.score).head
      case _ => collection.result.toArray.sortWith(_.score > _.score).head
    }
    (bestEntry.modelHyperParams, bestEntry.score)
  }

  private def evaluateStoppingScore(currentBestScore: Double, stopThreshold: Double): Boolean = {
    _optimizationStrategy match {
      case "minimize" => if (currentBestScore > stopThreshold) true else false
      case _ => if (currentBestScore < stopThreshold) true else false
    }
  }

  private def evaluateBestScore(runScore: Double, bestScore: Double): Boolean = {
    _optimizationStrategy match {
      case "minimize" => if (runScore < bestScore) true else false
      case _ => if (runScore > bestScore) true else false
    }
  }

  private def sortAndReturnAll(results: ArrayBuffer[LinearRegressionModelsWithResults]):
  Array[LinearRegressionModelsWithResults] = {
    _optimizationStrategy match {
      case "minimize" => results.result.toArray.sortWith(_.score < _.score)
      case _ => results.result.toArray.sortWith(_.score > _.score)
    }
  }

  private def sortAndReturnBestScore(results: ArrayBuffer[LinearRegressionModelsWithResults]): Double = {
    sortAndReturnAll(results).head.score
  }

  private def generateThresholdedParams(iterationCount: Int): Array[LinearRegressionConfig] = {

    val iterations = new ArrayBuffer[LinearRegressionConfig]

    var i = 0
    do {
      val elasticNetParams = generateRandomDouble("elasticNetParams", _linearRegressionNumericBoundaries)
      val fitIntercept = coinFlip()
      val loss = generateRandomString("loss", _linearRegressionStringBoundaries)
      val maxIter = generateRandomInteger("maxIter", _linearRegressionNumericBoundaries)
      val regParam = generateRandomDouble("regParam", _linearRegressionNumericBoundaries)
      val standardization = coinFlip()
      val tol = generateRandomDouble("tolerance", _linearRegressionNumericBoundaries)
      iterations += LinearRegressionConfig(elasticNetParams, fitIntercept, loss, maxIter, regParam, standardization,
        tol)
      i += 1
    } while (i < iterationCount)
    iterations.toArray
  }

  private def generateAndScoreLinearRegression(train: DataFrame, test: DataFrame,
                                               modelConfig: LinearRegressionConfig,
                                               generation: Int = 1): LinearRegressionModelsWithResults = {

    val regressionModel = configureModel(modelConfig)

    val builtModel = regressionModel.fit(train)

    val predictedData = builtModel.transform(test)

    val scoringMap = scala.collection.mutable.Map[String, Double]()

    for (i <- regressionMetrics) {
      val scoreEvaluator = new RegressionEvaluator()
        .setLabelCol(_labelCol)
        .setPredictionCol("prediction")
        .setMetricName(i)
      scoringMap(i) = scoreEvaluator.evaluate(predictedData)
    }
    LinearRegressionModelsWithResults(modelConfig, builtModel, scoringMap(_scoringMetric),
      scoringMap.toMap, generation)
  }

  private def runBattery(battery: Array[LinearRegressionConfig], generation: Int = 1): Array[LinearRegressionModelsWithResults] = {

    validateLabelAndFeatures(df, _labelCol, _featureCol)

    @volatile var results = new ArrayBuffer[LinearRegressionModelsWithResults]
    @volatile var modelCnt = 0
    val taskSupport = new ForkJoinTaskSupport(new ForkJoinPool(_parallelism))
    val runs = battery.par
    runs.tasksupport = taskSupport

    val currentStatus = f"Starting Generation $generation \n\t\t Completion Status: ${
      calculateModelingFamilyRemainingTime(generation, modelCnt)}%2.4f%%"

    println(currentStatus)
    logger.log(Level.INFO, currentStatus)

    runs.foreach { x =>
      val runId = java.util.UUID.randomUUID()
      println(s"Starting run $runId with Params: ${x.toString}")

      val kFoldBuffer = new ArrayBuffer[LinearRegressionModelsWithResults]

      for (_ <- _kFoldIteratorRange) {
        val Array(train, test) = genTestTrain(df, scala.util.Random.nextLong)
        kFoldBuffer += generateAndScoreLinearRegression(train, test, x)
      }
      val scores = new ArrayBuffer[Double]
      kFoldBuffer.map(x => {
        scores += x.score
      })

      val scoringMap = scala.collection.mutable.Map[String, Double]()
      for (a <- regressionMetrics) {
        val metricScores = new ListBuffer[Double]
        kFoldBuffer.map(x => metricScores += x.evalMetrics(a))
        scoringMap(a) = metricScores.sum / metricScores.length
      }
      val runAvg = LinearRegressionModelsWithResults(x, kFoldBuffer.result.head.model, scores.sum / scores.length,
        scoringMap.toMap, generation)
      results += runAvg
      modelCnt += 1
      val runScoreStatement = s"\tFinished run $runId with score: ${scores.sum / scores.length}"
      val progressStatement = f"\t\t Current modeling progress complete in family: ${
        calculateModelingFamilyRemainingTime(generation, modelCnt)}%2.4f%%"
      println(runScoreStatement)
      println(progressStatement)
      logger.log(Level.INFO, runScoreStatement)
      logger.log(Level.INFO, progressStatement)
    }

    sortAndReturnAll(results)

  }

  private def irradiateGeneration(parents: Array[LinearRegressionConfig], mutationCount: Int,
                          mutationAggression: Int, mutationMagnitude: Double): Array[LinearRegressionConfig] = {

    val mutationPayload = new ArrayBuffer[LinearRegressionConfig]
    val totalConfigs = modelConfigLength[LinearRegressionConfig]
    val indexMutation = if (mutationAggression >= totalConfigs) totalConfigs - 1 else totalConfigs - mutationAggression
    val mutationCandidates = generateThresholdedParams(mutationCount)
    val mutationIndeces = generateMutationIndeces(1, totalConfigs, indexMutation, mutationCount)

    for (i <- mutationCandidates.indices) {

      val randomParent = scala.util.Random.shuffle(parents.toList).head
      val mutationIteration = mutationCandidates(i)
      val mutationIndexIteration = mutationIndeces(i)

      mutationPayload += LinearRegressionConfig(
        if (mutationIndexIteration.contains(0)) geneMixing(randomParent.elasticNetParams,
          mutationIteration.elasticNetParams, mutationMagnitude)
        else randomParent.elasticNetParams,
        if (mutationIndexIteration.contains(1)) coinFlip(randomParent.fitIntercept,
          mutationIteration.fitIntercept, mutationMagnitude)
        else randomParent.fitIntercept,
        if (mutationIndexIteration.contains(2)) geneMixing(randomParent.loss,
          mutationIteration.loss)
        else randomParent.loss,
        if (mutationIndexIteration.contains(3)) geneMixing(randomParent.maxIter,
          mutationIteration.maxIter, mutationMagnitude)
        else randomParent.maxIter,
        if (mutationIndexIteration.contains(4)) geneMixing(randomParent.regParam,
          mutationIteration.regParam, mutationMagnitude)
        else randomParent.regParam,
        if (mutationIndexIteration.contains(5)) coinFlip(randomParent.standardization,
          mutationIteration.standardization, mutationMagnitude)
        else randomParent.standardization,
        if (mutationIndexIteration.contains(6)) geneMixing(randomParent.tolerance,
          mutationIteration.tolerance, mutationMagnitude)
        else randomParent.tolerance
      )
    }
    mutationPayload.result.toArray
  }

  private def continuousEvolution(): Array[LinearRegressionModelsWithResults] = {

    val taskSupport = new ForkJoinTaskSupport(new ForkJoinPool(_continuousEvolutionParallelism))

    var runResults = new ArrayBuffer[LinearRegressionModelsWithResults]

    var scoreHistory = new ArrayBuffer[Double]

    // Set the beginning of the loop and instantiate a place holder for holdling the current best score
    var iter: Int = 1
    var bestScore: Double = 0.0
    var rollingImprovement: Boolean = true
    var incrementalImprovementCount: Int = 0

    //TODO: evaluate this and see if this should be an early stopping signature!!!
    val earlyStoppingImprovementThreshold: Int = -10

    // Generate the first pool of attempts to seed the hyperparameter space
    //    var runSet = ParHashSet(generateThresholdedParams(_firstGenerationGenePool): _*)

    val totalConfigs = modelConfigLength[LinearRegressionConfig]

    var runSet = if(_modelSeedSet) {
      val genArray = new ArrayBuffer[LinearRegressionConfig]
      val startingModelSeed = generateLinearRegressionConfig(_modelSeed)
      genArray += startingModelSeed
      genArray ++= irradiateGeneration(Array(startingModelSeed), _firstGenerationGenePool, totalConfigs - 1,
        _geneticMixing)
      ParHashSet(genArray.result.toArray: _*)
    } else {
      ParHashSet(generateThresholdedParams(_firstGenerationGenePool): _*)
    }

    // Apply ForkJoin ThreadPool parallelism
    runSet.tasksupport = taskSupport

    do {

      runSet.foreach(x => {

        try {
          // Pull the config out of the HashSet
          runSet -= x

          // Run the model config
          val run = runBattery(Array(x), iter)

          runResults += run.head
          scoreHistory += run.head.score

          val (bestConfig, currentBestScore) = returnBestHyperParameters(runResults)

          bestScore = currentBestScore

          // Add a mutated version of the current best model to the ParHashSet
          runSet += irradiateGeneration(Array(bestConfig), 1,
            _continuousEvolutionMutationAggressiveness, _continuousEvolutionGeneticMixing).head

          // Evaluate whether the scores are staying static over the last configured rolling window.
          val currentWindowValues = scoreHistory.slice(
            scoreHistory.length - _continuousEvolutionRollingImprovementCount, scoreHistory.length)

          // Check for static values
          val staticCheck = currentWindowValues.toSet.size

          // If there is more than one value, proceed with validation check on whether the model is improving over time.
          if (staticCheck > 1) {
            val (early, later) = currentWindowValues.splitAt(scala.math.round(currentWindowValues.size / 2))
            if (later.sum / later.length < early.sum / early.length) {
              incrementalImprovementCount += 1
            }
            else {
              incrementalImprovementCount -= 1
            }
          } else {
            rollingImprovement = false
          }

          val statusReport = s"Current Best Score: $bestScore as of run: $iter with cumulative improvement count of: " +
            s"$incrementalImprovementCount"

          logger.log(Level.INFO, statusReport)
          println(statusReport)

          iter += 1

        } catch {
          case e: java.lang.NullPointerException =>
            val (bestConfig, currentBestScore) = returnBestHyperParameters(runResults)
            runSet += irradiateGeneration(Array(bestConfig), 1,
              _continuousEvolutionMutationAggressiveness, _continuousEvolutionGeneticMixing).head
            bestScore = currentBestScore
          case f: java.lang.ArrayIndexOutOfBoundsException =>
            val (bestConfig, currentBestScore) = returnBestHyperParameters(runResults)
            runSet += irradiateGeneration(Array(bestConfig), 1,
              _continuousEvolutionMutationAggressiveness, _continuousEvolutionGeneticMixing).head
            bestScore = currentBestScore
        }
      })
    } while (iter < _continuousEvolutionMaxIterations &&
      evaluateStoppingScore(bestScore, _continuousEvolutionStoppingScore)
      && rollingImprovement && incrementalImprovementCount > earlyStoppingImprovementThreshold)

    sortAndReturnAll(runResults)

  }

  def generateIdealParents(results: Array[LinearRegressionModelsWithResults]): Array[LinearRegressionConfig] = {
    val bestParents = new ArrayBuffer[LinearRegressionConfig]
    results.take(_numberOfParentsToRetain).map(x => {
      bestParents += x.modelHyperParams
    })
    bestParents.result.toArray
  }

  def evolveParameters(): Array[LinearRegressionModelsWithResults] = {

    var generation = 1
    // Record of all generations results
    val fossilRecord = new ArrayBuffer[LinearRegressionModelsWithResults]

    val totalConfigs = modelConfigLength[LinearRegressionConfig]

    val primordial = if (_modelSeedSet) {
      val generativeArray = new ArrayBuffer[LinearRegressionConfig]
      val startingModelSeed = generateLinearRegressionConfig(_modelSeed)
      generativeArray += startingModelSeed
      generativeArray ++= irradiateGeneration(Array(startingModelSeed), _firstGenerationGenePool, totalConfigs - 1,
        _geneticMixing)
      runBattery(generativeArray.result.toArray, generation)
    } else {
      runBattery(generateThresholdedParams(_firstGenerationGenePool), generation)
    }

    fossilRecord ++= primordial
    generation += 1

    var currentIteration = 1

    if (_earlyStoppingFlag) {

      var currentBestResult = sortAndReturnBestScore(fossilRecord)

      if (evaluateStoppingScore(currentBestResult, _earlyStoppingScore)) {
        while (currentIteration <= _numberOfMutationGenerations &&
          evaluateStoppingScore(currentBestResult, _earlyStoppingScore)) {

          val mutationAggressiveness = _generationalMutationStrategy match {
            case "linear" => if (totalConfigs - (currentIteration + 1) < 1) 1 else
              totalConfigs - (currentIteration + 1)
            case _ => _fixedMutationValue
          }

          // Get the sorted state
          val currentState = sortAndReturnAll(fossilRecord)

          val evolution = irradiateGeneration(generateIdealParents(currentState), _numberOfMutationsPerGeneration,
            mutationAggressiveness, _geneticMixing)

          var evolve = runBattery(evolution, generation)
          generation += 1
          fossilRecord ++= evolve

          val postRunBestScore = sortAndReturnBestScore(fossilRecord)

          if (evaluateBestScore(postRunBestScore, currentBestResult)) currentBestResult = postRunBestScore

          currentIteration += 1

        }

        sortAndReturnAll(fossilRecord)

      } else {
        sortAndReturnAll(fossilRecord)
      }
    } else {
      (1 to _numberOfMutationGenerations).map(i => {

        val mutationAggressiveness = _generationalMutationStrategy match {
          case "linear" => if (totalConfigs - (i + 1) < 1) 1 else totalConfigs - (i + 1)
          case _ => _fixedMutationValue
        }

        val currentState = sortAndReturnAll(fossilRecord)

        val evolution = irradiateGeneration(generateIdealParents(currentState), _numberOfMutationsPerGeneration,
          mutationAggressiveness, _geneticMixing)

        var evolve = runBattery(evolution, generation)
        generation += 1
        fossilRecord ++= evolve

      })

      sortAndReturnAll(fossilRecord)

    }
  }

  def evolveBest(): LinearRegressionModelsWithResults = {
    evolveParameters().head
  }

  def generateScoredDataFrame(results: Array[LinearRegressionModelsWithResults]): DataFrame = {

    import spark.sqlContext.implicits._

    val scoreBuffer = new ListBuffer[(Int, Double)]
    results.map(x => scoreBuffer += ((x.generation, x.score)))
    val scored = scoreBuffer.result
    spark.sparkContext.parallelize(scored)
      .toDF("generation", "score").orderBy(col("generation").asc, col("score").asc)
  }

  def evolveWithScoringDF():
  (Array[LinearRegressionModelsWithResults], DataFrame) = {

    val evolutionResults = _evolutionStrategy match {
      case "batch" => evolveParameters()
      case "continuous" => continuousEvolution()
    }

    (evolutionResults, generateScoredDataFrame(evolutionResults))
  }


}
