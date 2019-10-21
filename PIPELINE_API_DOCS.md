# Pipeline API for the AutoML-Toolkit 

The AutoML-Toolkit is an automated ML solution for Apache Spark.  It provides common data cleansing and feature 
engineering support, automated hyper-parameter tuning through distributed genetic algorithms, and model tracking 
integration with MLFlow.  It currently supports Supervised Learning algorithms that are provided as part of Spark Mllib.

## General Overview

The AutoML toolkit exposes the following pipeline-related APIs via [FamilyRunner](src/main/scala/com/databricks/labs/automl/executor/FamilyRunner.scala)


### Full Predict pipeline API:
```text
executeWithPipeline()
```
This pipeline API works with the existing configuration object (and overrides) as listed [here](APIDOCS.md), 
but it returns the following output
```text
FamilyFinalOutputWithPipeline(
  familyFinalOutput: FamilyFinalOutput,
  bestPipelineModel: Map[String, PipelineModel]
)
```
As noted, ```bestPipelineModel``` contains a key, value pair of a model family 
and the best pipeline model (based on the selected ```scoringOptimizationStrategy```)

Example:
```scala
import com.databricks.labs.automl.executor.config.ConfigurationGenerator
import com.databricks.labs.automl.executor.FamilyRunner
import org.apache.spark.ml.PipelineModel

val data = spark.table("ben_demo.adult_data")
val overrides = Map(
  "labelCol" -> "label", "mlFlowLoggingFlag" -> false,
  "scalingFlag" -> true, "oneHotEncodeFlag" -> true,
  "pipelineDebugFlag" -> true
)
val randomForestConfig = ConfigurationGenerator
  .generateConfigFromMap("RandomForest", "classifier", overrides)
val runner = FamilyRunner(data, Array(randomForestConfig))
  .executeWithPipeline()

runner.bestPipelineModel("RandomForest").transform(data)

//Serialize it
runner.bestPipelineModel("RandomForest").write.overwrite().save("tmp/predict-pipeline-1")

// Load it for running inference
val pipelineModel = PipelineModel.load("tmp/predict-pipeline-1")
val predictDf = pipelineModel.transform(data)
```

 
### Feature engineering pipeline API:
```text
generateFeatureEngineeredPipeline(verbose: Boolean = false)
```
@param ```verbose```: If set to true, any dataset transformed with this feature engineered pipeline will include all
                      input columns for the vector assembler stage
                      
This API builds a feature engineering pipeline based on the existing configuration object (and overrides) 
as listed [here](APIDOCS.md). It returns back the output of type ```Map[String, PipelineModel]``` where ```(key -> value)``` are
```(modelFamilyName -> featureEngPipelineModel)```

Example:
```scala
import com.databricks.labs.automl.executor.config.ConfigurationGenerator
import com.databricks.labs.automl.executor.FamilyRunner
import org.apache.spark.ml.PipelineModel

val data = spark.table("ben_demo.adult_data")
val overrides = Map(
  "labelCol" -> "label", "mlFlowLoggingFlag" -> false,
  "scalingFlag" -> true, "oneHotEncodeFlag" -> true,
  "pipelineDebugFlag" -> true
)
val randomForestConfig = ConfigurationGenerator.generateConfigFromMap("RandomForest", "classifier", overrides)
val runner = FamilyRunner(data, Array(randomForestConfig))
  .generateFeatureEngineeredPipeline(verbose = true)

runner("RandomForest")
.write
.overwrite()
.save("tmp/feat-eng-pipeline-1")

val featEngDf = PipelineModel
.load("tmp/feat-eng-pipeline-1")
.transform(data)
```

### Pipeline Configurations
As noted above, all the pipeline APIs will work with the existing configuration objects. In addition to those, pipeline API
exposes the following configurations:

```@text
default: false
pipelineDebugFlag: A Boolean flag for the pipeline logging purposes. When turned on, each stage in a pipeline execution 
will print and log out a lot of useful information that can be used to track transformations for debugging/troubleshooting 
puproses
```

