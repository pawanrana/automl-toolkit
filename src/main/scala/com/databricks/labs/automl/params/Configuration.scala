package com.databricks.labs.automl.params

case class MainConfig(modelFamily: String,
                      labelCol: String,
                      featuresCol: String,
                      naFillFlag: Boolean,
                      varianceFilterFlag: Boolean,
                      outlierFilterFlag: Boolean,
                      pearsonFilteringFlag: Boolean,
                      covarianceFilteringFlag: Boolean,
                      oneHotEncodeFlag: Boolean,
                      scalingFlag: Boolean,
                      dataPrepCachingFlag: Boolean,
                      autoStoppingFlag: Boolean,
                      autoStoppingScore: Double,
                      featureImportanceCutoffType: String,
                      featureImportanceCutoffValue: Double,
                      dateTimeConversionType: String,
                      fieldsToIgnoreInVector: Array[String],
                      numericBoundaries: Map[String, (Double, Double)],
                      stringBoundaries: Map[String, List[String]],
                      scoringMetric: String,
                      scoringOptimizationStrategy: String,
                      fillConfig: FillConfig,
                      outlierConfig: OutlierConfig,
                      pearsonConfig: PearsonConfig,
                      covarianceConfig: CovarianceConfig,
                      scalingConfig: ScalingConfig,
                      geneticConfig: GeneticConfig,
                      mlFlowLoggingFlag: Boolean,
                      mlFlowLogArtifactsFlag: Boolean,
                      mlFlowConfig: MLFlowConfig,
                      inferenceConfigSaveLocation: String,
                      dataReductionFactor: Double)

// TODO: Change MainConfig to use this case class definition.
case class DataPrepConfig(naFillFlag: Boolean,
                          varianceFilterFlag: Boolean,
                          outlierFilterFlag: Boolean,
                          pearsonFilterFlag: Boolean,
                          covarianceFilterFlag: Boolean,
                          scalingFlag: Boolean)

case class MLFlowConfig(mlFlowTrackingURI: String,
                        mlFlowExperimentName: String,
                        mlFlowAPIToken: String,
                        mlFlowModelSaveDirectory: String,
                        mlFlowLoggingMode: String,
                        mlFlowBestSuffix: String,
                        mlFlowCustomRunTags: Map[String, String])

case class FillConfig(numericFillStat: String,
                      characterFillStat: String,
                      modelSelectionDistinctThreshold: Int)

case class OutlierConfig(filterBounds: String,
                         lowerFilterNTile: Double,
                         upperFilterNTile: Double,
                         filterPrecision: Double,
                         continuousDataThreshold: Int,
                         fieldsToIgnore: Array[String])

case class PearsonConfig(filterStatistic: String,
                         filterDirection: String,
                         filterManualValue: Double,
                         filterMode: String,
                         autoFilterNTile: Double)

case class CovarianceConfig(correlationCutoffLow: Double,
                            correlationCutoffHigh: Double)

case class FirstGenerationConfig(permutationCount: Int,
                                 indexMixingMode: String,
                                 arraySeed: Long)

case class GeneticConfig(parallelism: Int,
                         kFold: Int,
                         trainPortion: Double,
                         trainSplitMethod: String,
                         trainSplitChronologicalColumn: String,
                         trainSplitChronologicalRandomPercentage: Double,
                         seed: Long,
                         firstGenerationGenePool: Int,
                         numberOfGenerations: Int,
                         numberOfParentsToRetain: Int,
                         numberOfMutationsPerGeneration: Int,
                         geneticMixing: Double,
                         generationalMutationStrategy: String,
                         fixedMutationValue: Int,
                         mutationMagnitudeMode: String,
                         evolutionStrategy: String,
                         continuousEvolutionMaxIterations: Int,
                         continuousEvolutionStoppingScore: Double,
                         continuousEvolutionParallelism: Int,
                         continuousEvolutionMutationAggressiveness: Int,
                         continuousEvolutionGeneticMixing: Double,
                         continuousEvolutionRollingImprovementCount: Int,
                         modelSeed: Map[String, Any],
                         hyperSpaceInference: Boolean,
                         hyperSpaceInferenceCount: Int,
                         hyperSpaceModelType: String,
                         hyperSpaceModelCount: Int,
                         initialGenerationMode: String,
                         initialGenerationConfig: FirstGenerationConfig)

case class ScalingConfig(scalerType: String,
                         scalerMin: Double,
                         scalerMax: Double,
                         standardScalerMeanFlag: Boolean,
                         standardScalerStdDevFlag: Boolean,
                         pNorm: Double)
