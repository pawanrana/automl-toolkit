# Providentia (AutoML)

Providentia is an automated ML solution for Apache Spark.  It provides common data cleansing and feature engineering support, automated hyper-parameter tuning through distributed genetic algorithms, and model tracking integration with MLFlow.  It currently supports Supervised Learning algorithms that are provided as part of Spark Mllib.

## General Overview

Providentia is a multi-layer API that can be used in several different ways:
1. Full Automation (high-level API) by using the AutomationRunner class and utilzing the .run() public method
2. Mid-level Automation through use of individual component API's (DataPrep / AutomationRunner public methods)
3. Low-level API's for Hyper-parameter tuning

## Full Automation

At the highest level of the API, the AutomationRunner, using defaults, requires only a Spark Dataframe to be supplied to the class instantiation.  Running the main method .run() will return 4 objects within a tuple:

#### Automation Return Values

##### The Generic Run Results, consisting of type: `Array[GenericModelReturn]`

```scala
case class GenericModelReturn(
                               hyperParams: Map[String, Any],
                               model: Any,
                               score: Double,
                               metrics: Map[String, Double],
                               generation: Int
                             )
```
These elements are: 
* hyperParams -> The elements that were utilized in the hyper-parameter tuning run, based on the type of model that was trained and validated.
* model -> The model artifact of the run, stored as an `[Any]` type.  Accessing this directly from this return element will require casting to the appropriate instance type 
  (e.g. ```val myBestModel = modelReturn(0).model.asInstanceOf[RandomForestRegressionModel]```)
* score -> The specified optimization score for the experiment run (i.e. default for classification is 'f1')
* metrics -> All available scores for the detected or specified model type (regression or classification). (i.e. for classification tasks, the metrics are: "f1", "weightedPrecision", "weightedRecall", and "accuracy")
* generation -> For batch configuration mode, the generation of evolution that the training and validation took place.  For continuous configuration mode, the iteration number of the individual run.

##### The Generational Average Scores as: 

* `Map[Int, (Double, Double)]`

<p>This data corresponds to Map[Generation, (Average Optimization Score for the Generation, Stddev of the Optimization Score for the Generation)]
</p>

##### A Spark Dataframe representation of the Generational Average Scores, consisting of 2 columns: 

* `Generation[Int]` 
* `Score[Double]`

##### A Spark Dataframe representation of the Generational Run Results, consisting of 5 columns: 

* model_family[String]
* model_type[String]
* generation[Int] 
* generation_mean_score[Double] 
* generation_std_dev_score[Double]

```text
NOTE: If using MLFlow integration, all of this data, in raw format, will be recorded and stored automatically.
```

#### Example usage of the full AutomationRunner API (Basic)

```scala
import com.databricks.spark.automatedml.AutomationRunner

// Read data from a pre-defined Delta Table that has the labeled data in a column named 'label'
val myData: DataFrame = spark.table("my_db.my_data")

val (fullReport, generationalReport, generationalScoreDF, generationalReportDF) = new AutomationRunner(myData).run()

```
This will extract the default configuration set in Providentia, run through feature engineering tasks, data cleanup, categorical data conversion, vectorization, modeling, and scoring.  However, in most cases, overriding these default values is desirable.  Overriding is done through setters, like other aspects of SparkMllib.

#### Example overriding of defaults

```scala
import com.databricks.spark.automatedml.AutomationRunner

// Read data from a pre-defined Delta Table that has the labeled data in a column named 'label'
val myData: DataFrame = spark.table("my_db.my_other_data")

val automationConf = new AutomationRunner(myData)
  .setModelingFamily("RandomForest")
  .setLabelCol("predicted_value")
  .setParallelism(6)
  .setKFold(5)
  .setTrainPortion(0.7)
  .setNumberOfGenerations(12)
  .setNumberOfParentsToRetain(2)
  .setGeneticMixing(0.6)
  .pearsonFilterOff()
  .mlFlowLoggingOff()
  .setFeatureImportanceCutoffType("count")
  .setFeatureImportanceCutoffValue(0.2)
  .autoStoppingOn()
  .setAutoStoppingScore(0.94)
  .setTrainSplitMethod("chronological")
  .setTrainSplitChronologicalColumn("date_timestamp")
  .setFieldsToIgnoreInVector(Array("time_event_occur", "user_id"))
  .setTrainSplitChronologicalRandomPercentage(8)
  
val (fullReport, generationalReport, generationalScoreDF, generationalReportDF) = new AutomationRunner(myData).run()
```

The usage above overrides a number of the defaults (discussed in detail below).

#### Full Configuration for the AutomationRunner() class 
```scala
case class MainConfig(
                       modelFamily: String,
                       labelCol: String,
                       featuresCol: String,
                       naFillFlag: Boolean,
                       varianceFilterFlag: Boolean,
                       outlierFilterFlag: Boolean,
                       pearsonFilteringFlag: Boolean,
                       covarianceFilteringFlag: Boolean,
                       scalingFlag: Boolean,
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
                       fillConfig: FillConfig(
                                              numericFillStat: String,
                                              characterFillStat: String,
                                              modelSelectionDistinctThreshold: Int
                                             ),
                       outlierConfig: OutlierConfig(
                                                    filterBounds: String,
                                                    lowerFilterNTile: Double,
                                                    upperFilterNTile: Double,
                                                    filterPrecision: Double,
                                                    continuousDataThreshold: Int,
                                                    fieldsToIgnore: Array[String]
                                                   ),
                       pearsonConfig: PearsonConfig(
                                                    filterStatistic: String,
                                                    filterDirection: String,
                                                    filterManualValue: Double,
                                                    filterMode: String,
                                                    autoFilterNTile: Double
                                                   ),
                       covarianceConfig: CovarianceConfig(
                                                          correlationCutoffLow: Double,
                                                          correlationCutoffHigh: Double
                                                         ),
                       scalingConfig: ScalingConfig(
                                                    scalerType: String,
                                                    scalerMin: Double,
                                                    scalerMax: Double,
                                                    standardScalerMeanFlag: Boolean,
                                                    standardScalerStdDevFlag: Boolean,
                                                    pNorm: Double
                                                   ),
                       geneticConfig: GeneticConfig(
                                                    parallelism: Int,
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
                                                    continuousEvolutionRollingImprovementCount: Int
                                                   ),
                       mlFlowLoggingFlag: Boolean,
                       mlFlowConfig: MLFlowConfig(
                                                  mlFlowTrackingURI: String,
                                                  mlFlowExperimentName: String,
                                                  mlFlowAPIToken: String,
                                                  mlFlowModelSaveDirectory: String
                                                 )
                     )
```
Access to override each of the defaults that are provided is done through getters and setters that are shown below 
```text
(note that all configs avilable to override are shown here.  
Some demonstrated values below override other setters' behaviors.
(i.e. setting scaler type to 'normalize' ignores the scalerMin and scalerMax setter values;
the below example is simply used to expose all available options and would not be an optimal end-use config))
```

##### Setters

```scala

import com.databricks.spark.automatedml.AutomationRunner

// Read data from a pre-defined Delta Table that has the labeled data in a column named 'label'
val myData: DataFrame = spark.table("my_db.my_sample_data")

val fullConfig = new AutomationRunner(myData)
    .setModelingFamily("MLPC")
    .setLabelCol("ground_truth")
    .setFeaturesCol("feature_vector")
    .naFillOff()                        // alternative: .naFillOn()
    .varianceFilterOn()                 // alternative: .varianceFilterOff()
    .outlierFilterOff()                 // alternative: .outlierFilterOn()
    .pearsonFilterOff()                 // alternative: .pearsonFilterOn()
    .covarianceFilterOn()               // alternative: .covarianceFilterOff()
    .scalingOn()                        // alternative: .scalingOff()
    .setStandardScalerMeanFlagOff()     // alternative: .setStandardScalerMeanFlagOn()
    .setStandardScalerStdDevFlagOff()   // alternative: .setStandardScalerMeanFlagOn()  
    .mlFlowLoggingOn()                  // alternative: .mlFlowLoggingOff()  
    .autoStoppingOff()                  // alternative: .autoStoppingOn()
    .setNumericBoundaries(Map(
                              "layers" -> Tuple2(4.0, 20.0),
                              "maxIter" -> Tuple2(10.0, 200.0),
                              "stepSize" -> Tuple2(0.01, 0.5),
                              "tol" -> Tuple2(1E-9, 1E-6),
                              "hiddenLayerSizeAdjust" -> Tuple2(5.0, 50.0)
                            ))
    .setStringBoundaries(Map(
                             "solver" -> List("l-bfgs")
                             ))
    .setNumericFillStat("median")
    .setCharacterFillStat("min")
    .setDateTimeConversionType("unix")
    .setFieldsToIgnoreInVector(Array("user_id", "event_time", "system_id"))
    .setModelSelectionDistinctThreshold(15) 
    .setFilterBounds("upper")
    .setLowerFilterNTile(0.01)
    .setUpperFilterNTile(0.99)
    .setFilterPrecision(0.9)
    .setContinuousDataThreshold(100)
    .setFieldsToIgnore(Array("user_id", "event_time", "system_id"))
    .setPearsonFilterStatistic("pvalue")
    .setPearsonFilterDirection("greater")
    .setPearsonFilterManualValue(0.5)
    .setPearsonFilterMode("auto")
    .setPearsonAutoFilterNTile(0.8)
    .setCorrelationCutoffLow(-0.95)
    .setCorrelationCutoffHigh(0.95)
    .setScalerType("normalize")
    .setScalerMin(0.0)                 
    .setScalerMax(1.0)
    .setPNorm(3)                        
    .setParallelism(8)
    .setKFold(4)
    .setTrainPortion(0.85)
    .setTrainSplitMethod("chronological")
    .setTrainSplitChronologicalColumn("event_date")
    .setTrainSplitChronologicalRandomPercentage(5.0)
    .setSeed(12345L)
    .setFirstGenerationGenePool(30)
    .setNumberOfGenerations(8)
    .setNumberOfParentsToRetain(2)
    .setNumberOfMutationsPerGeneration(10)
    .setGeneticMixing(0.5)
    .setGenerationalMutationStrategy("fixed")
    .setMlFlowTrackingURI("https://mydatabrickscluster.cloud.databricks.com")
    .setMlFlowExperimentName("testing")
    .setMlFlowAPIToken(dbutils.notebook.getContext().apiToken.get)
    .setMlFlowModelSaveDirectory("/ml/mymodels/testModel")
    .setAutoStoppingScore(0.88)
    .setFeatureImportanceCutoffType("count")
    .setFeatureImportanceCutoffValue(20.0)
    .setEvolutionStrategy("continuous")
    .setContinuousEvolutionMaxIterations(300)
    .setContinuousEvolutionStoppingScore(0.88)
    .setContinuousEvolutionParallelism(6)
    .setContinuousEvolutionMutationAggressiveness(3)
    .setContinuousEvolutionGeneticMixing(0.4)
    .setContinuousEvolutionRollingImprovementCount(15)
```
##### Note
```text
There are public setters exposed to set the entire config 
(i.e. defining a MainConfig case class object, which can then be used through the public setter 
.setMainConfig(myMainConfig: MainConfig) )
```


If at any time the current configuration is needed to be exposed, each setter, as well as the overall main Config and 
the individual module configs can be acquired by using the appropriate getter.  For example:
```scala
import com.databricks.spark.automatedml.AutomationRunner

val myData: DataFrame = spark.table("my_db.my_sample_data")

val fullConfig = new AutomationRunner(myData)

fullConfig.getAutoStoppingScore

```

> NOTE: MlFlow configs are not exposed via getters.  This is to prevent accessing the API Key and printing it by an end-user.

# Module Functionality

This API is split into two main portions:
* Feature Engineering / Data Prep / Vectorization
* Automated HyperParameter Tuning

Both modules use a few common variables among them that require consistency for the chained sequence of events to function correctly.
Aside from those, all others are specific to those main modules (discussed in detail below).

### Common Module Settings
###### Label Column Name

Setter: `.setLabelCol(<String>)`

```text
Default: "label"

This is the 'predicted value' for use in supervised learning.  
    If this field does not exist within the dataframe supplied, an assertion exception will be thrown 
    once a method (other than setters/getters) is called on the AutomationRunner() object.
```
###### Feature Column Name

Setter: `.setFeaturesCol(<String>)`

```text
Default: "features"

Purely cosmetic setting that ensures consistency throughout all of the modules within Providentia.  
    
[Future Feature] In a future planned release, new accessor methods will make this setting more relevant, 
    as a validated prediction data set will be returned along with run statistics.
```
###### Fields to ignore in vector

Setter: `.setFieldsToIgnoreInVector(<Array[String]>)`

```text
Default: Array.empty[String]

Provides a means for ignoring specific fields in the Dataframe from being included in any DataPrep feature 
engineering tasks, filtering, and exlusion from the feature vector for model tuning.

This is particularly useful if there is a need to perform follow-on joins to other data sets after model 
training and prediction is complete.
```
###### Model Family

Setter: `.setModelingFamily(<String>)`

```text
Default: "RandomForest"

Sets the modeling family (Spark Mllib) to be used to train / validate.
```

> For model families that support both Regression and Classification, the parameter value 
`.setModelDistinctThreshold(<Int>)` is used to determine which to use.  Distinct values in the label column, 
if below the `modelDistinctThreshold`, will use a Classifier flavor of the Model Family.  Otherwise, it will use the Regression Type.

Currently supported models:
* "RandomForest" - [Random Forest Classifier](http://spark.apache.org/docs/latest/ml-classification-regression.html#random-forest-classifier) or [Random Forest Regressor](http://spark.apache.org/docs/latest/ml-classification-regression.html#random-forest-regression)
* "GBT" - [Gradient Boosted Trees Classifier](http://spark.apache.org/docs/latest/ml-classification-regression.html#gradient-boosted-tree-classifier) or [Gradient Boosted Trees Regressor](http://spark.apache.org/docs/latest/ml-classification-regression.html#gradient-boosted-tree-regression)
* "Trees" - [Decision Tree Classifier](http://spark.apache.org/docs/latest/ml-classification-regression.html#decision-tree-classifier) or [Decision Tree Regressor](http://spark.apache.org/docs/latest/ml-classification-regression.html#decision-tree-regression)
* "LinearRegression" - [Linear Regressor](http://spark.apache.org/docs/latest/ml-classification-regression.html#linear-regression)
* "LogisticRegression" - [Logistic Regressor](http://spark.apache.org/docs/latest/ml-classification-regression.html#logistic-regression) (supports both Binomial and Multinomial)
* "MLPC" - [Multi-Layer Perceptron Classifier](http://spark.apache.org/docs/latest/ml-classification-regression.html#multilayer-perceptron-classifier)
* "SVM" - [Linear Support Vector Machines](http://spark.apache.org/docs/latest/ml-classification-regression.html#linear-support-vector-machine)

###### Date Time Conversion Type
```text
Default: "split"

Available options: "split", "unix"
```
This setting determines how to handle DateTime type fields.  
* In the "unix" setting mode, the datetime type is converted to `[Double]` type of the `[Long]` Unix timestamp in seconds.

* In the "split" setting mode, the date is transformed into its constituent parts and adding each part to seperate fields.
> By default, extraction is at maximum precision -> (year, month, day, hour, minute, second)


## Data Prep
The Data Prep Module is intended to automate many of the typical feature engineering tasks involved in prototyping of Machine Learning models.
It includes some necessary tasks (filling NA values and removing zero-information fields), and some optional data 
clean-up features that provide a trade-off between potential gains in <u>modeling accuracy</u> vs. <u>runtime complexity</u>.  

### Fill Null Values
```text
Default: ON
Turned off via setter .naFillOff()
```
> NOTE: It is **HIGHLY recommended** to leave this turned on.  If there are Null values in the feature vector, exceptions may be thrown.

This module allows for filling both numeric values and categorical & string values.

##### Options
```text
Member of configuration case class FillConfig()

setters: 
.setNumericFillStat(<String>)
.setCharacterFillStat(<String>) 
.setModelSelectionDistinctThreshold(<Int>)
```

###### Numeric Fill Stat
```text 
Default: "mean"
```
* For all numeric types (or date/time types that have been cast to numeric types)
* Allowable fill statistics: 
1. <b>"min"</b> - minimum sorted value from all distinct values of the field
2. <b>"25p"</b> - 25th percentile (Q1 / Lower IQR value) of the ascending sorted data field
3. <b>"mean"</b> - the mean (average) value of the data field
4. <b>"median"</b> - median (50th percentile / Q2) value of the ascending sorted data field
5. <b>"75p"</b> - 75th percentile (Q3 / Upper IQR value) of the ascending sorted data field
6. <b>"max"</b> - maximum sorted value from all distinct values of the field

###### Character Fill Stat
```text 
Default: "max"
```
* For all categorical, string, and byte type fields.
* Allowable fill statistics are the same as for numeric types.  However, it is <u> highly recommended to use either <b> "min" or "max"</b></u>
as this will fill in either the most frequent occuring (max) or least frequently occuring (min) term.

###### Model Selection Distinct Threshold
```text 
Default: 10
```
This setting is used for detecting numeric fields that are not of a continuous nature, but rather are ordinal or 
categorical.  While iterating through each numeric field, a check is made on distinct count on the feature field.  
Any fields that are <b>below</b> this threshold setting are handled as character fields and will be filled as per the 
setting of `.setCharacterFillStat()`

### Filter Zero Variance Features
```text
Default: ON
Turned off via setter .varianceFilterOff()

NOTE: It is HIGHLY recommended to leave this turned on.
Feature fields with zero information gain increase overall processing time and provide no real value to the model.
```
There are no options associated with module.

### Filter Outliers
```text
Default: OFF
Turned on via setter .outlierFitlerOn()
```

This module allows for detecting outliers from within each field, setting filter thresholds 
(either automatically or manually), and allowing for either tail reduction or two-sided reduction.
Including outliers in some families of machine learning models will result in dramatic overfitting to those values.  

> NOTE: It is recommended to turn this option ON if not using RandomForest, GBT, or Trees models.

##### Options
```text
Member of configuration case class OutlierConfig()

setters:
.setFilterBounds(<String>)
.setLowerFilterNTile(<Double>)
.setUpperFilterNTile(<Double>)
.setFilterPrecision(<Double>)
.setContinuousDataThreshold(<Int>)
.setFieldsToIgnore(<Array[String]>)
```

###### Filter Bounds
```text
Default: "both"
```
Filtering both 'tails' is only recommended if the nature of the data set's input features are of a normal distribution.
If there is skew in the distribution of the data, a left or right-tailed filter should be employed.
The allowable modes are:
1. "lower" - useful for left-tailed distributions (rare)
2. "both" - useful for normally distributed data (common)
3. "upper" - useful for right-tailed distributions (common)
<p></p>

For Further reading: [Distributions](https://en.wikipedia.org/wiki/List_of_probability_distributions#With_infinite_support)

###### Lower Filter NTile
```text
Default: 0.02
```
Filters out values (rows) that are below the specified quantile level based on a sort of the field's data in ascending order.
> Only applies to modes "both" and "lower"

###### Upper Filter NTile
```text
Default: 0.98
```
Filters out values (rows) that are above the specified quantile threshold based on the ascending sort of the field's data.
> Only applies to modes "both" and "upper"

###### Filter Precision
```text
Default: 0.01
```
Determines the level of precision in the calculation of the N-tile values of each field.  
Setting this number to a lower value will result in additional shuffling and computation.
The algorithm that uses the filter precision is `approx_count_distinct(columnName: String, rsd: Double)`.  
The lower that this value is set, the more accurate it is (setting it to 0.0 will be an exact count), but the more 
shuffling (computationally expensive) will be required to calculate the value.
> NOTE: **Restricted Value** range: 0.0 -> 1.0

###### Continuous Data Threshold
```text
Default: 50
```
Determines an exclusion filter of unique values that will be ignored if the unique count of the field's values is below the specified threshold.
> Example:

| Col1 	| Col2 	| Col3 	| Col4 	| Col5 	|
|:----:	|:----:	|:----:	|:----:	|:----:	|
|   1  	|  47  	|   3  	|   4  	|   1  	|
|   1  	|  54  	|   1  	|   0  	|  11  	|
|   1  	| 9999 	|   0  	|   0  	|   0  	|
|   0  	|   7  	|   3  	|   0  	|  11  	|
|   1  	|   1  	|   0  	|   0  	|   1  	|

> In this example data set, if the continuousDataThreshold value were to be set at 4, the ignored fields would be: Col1, Col3, Col4, and Col5.
> > Col2, having 5 unique entries, would be evaluated by the outlier filtering methodology and, provided that upper range filtering is being done, Row #3 (value entry 9999) would be filtered out with an UpperFilterNTile setting of 0.8 or lower. 

###### Fields To Ignore
```text
Default: Array("")
```
Optional configuration that allows certain fields to be exempt (ignored) by the outlier filtering processing.  
Any column names that are supplied to this setter will not be used for row filtering.

### Covariance Filtering
```text
Default: OFF
Turned on via setter .covarianceFilterOn()
```

Covariance Filtering is a Data Prep module that iterates through each element of the feature space 
(fields that are intended to be part of the feature vector), calculates the pearson correlation coefficient between each 
feature to every other feature, and provides for the ability to filter out highly positive or negatively correlated 
features to prevent fitting errors.

Further Reading: [Pearson Correlation Coefficient](https://en.wikipedia.org/wiki/Pearson_correlation_coefficient)

> NOTE: This algorithm, although operating within a concurrent thread pool, can be costly to execute.  
In sequential mode (parallelism = 1), it is O(n * log(n)) and should be turned on only for an initial exploratory phase of determining predictive power.  

##### General Algorithm example

Given a data set:

| A 	| B 	| C 	| D 	| label |
|:----:	|:----:	|:----:	|:----:	|:----: |
|   1  	|  94 	|   5  	|   10 	|   1   |
|   2  	|   1  	|   4  	|   20 	|   0   | 
|   3  	|  22 	|   3  	|   30 	|   1   |
|   4  	|   5  	|   2  	|   40 	|   0   |
|   5  	|   5  	|   1  	|   50 	|   0   |

Each of the fields A:B:C:D would be compared to one another, the pearson value would be calculated, and a filter will occur.

* A->B 
* A->C 
* A->D 
* B->C 
* B->D 
* C->D

There is a perfect linear negative correlation present between A->C, a perfect postitive linear correlation between 
A->D, and a perfect linear negative correlation between C->D.
However, evaluation of C->D will not occur, as both C and D will be filtered out due to the correlation coefficient 
threshold.
The resultant data set from this module would be, after filtering: 

| A 	| B 	| label |
|:----:	|:----:	|:----: |
|   1  	|  94 	|   1   |
|   2  	|   1  	|   0   | 
|   3  	|  22 	|   1   |
|   4  	|   5  	|   0   |
|   5  	|   5  	|   0   |

##### Options
```text
Member of configuration case class CovarianceConfig()

setters:
.setCorrelationCutoffLow(<Double>)
.setCorrelationCutoffHigh(<Double>)
```
> NOTE: Max supported value for `.setCorrelationCutoffHigh` is 1.0

> NOTE: Max supported value for `.setCorrelationCutoffLow` is -1.0

> There are no settings for determining left / right / both sided filtering.  Instead, the cutoff values can be set to achieve this.
> > i.e. to only filter positively correlated values, apply the setting: `.setCorrelationCutoffLow(-1.0)` which would only filter
> > fields that are **exactly** negatively correlated (linear negative correlation)

###### Correlation Cutoff Low
```text
Default: -0.8
```
The setting at below which the right-hand comparison field will be filtered out of the data set, provided that the 
pearson correlation coefficient between left->right fields is below this threshold.

###### Correlation Cutoff High
```text
Default: 0.8
```
The upper positive correlation filter level.  Correlation Coefficients above this level setting will be removed from the data set.

### Pearson Filtering
```text
Default: OFF
Turned on via setter .pearsonFilterOn()
```
This module will perform validation of each field of the data set (excluding fields that have been added to 
`.setFieldsToIgnoreInVector(<Array[String]>])` and any fields that have been culled by any previous optional DataPrep 
feature engineering module) to the label column that has been set (`.setLabelCol()`).

The mechanism for comparison is a ChiSquareTest that utilizes one of three currently supported modes (listed below)

[Spark Doc - ChiSquaredTest](http://spark.apache.org/docs/latest/api/scala/index.html#org.apache.spark.ml.stat.ChiSquareTest$)

[Pearson's Chi-squared test](https://en.wikipedia.org/wiki/Chi-squared_test)

##### Options
```text
Member of configuration case class PearsonConfig()

setters:
.setPearsonFilterStatistic(<String>)
.setPearsonFilterDirection(<String>)
.setPearsonFilterManualValue(<Double>)
.setPearsonFilterMode(<String>)
.setPearsonAutoFilterNTile(<Double>)
```

###### Pearson Filter Statistic
```text
Default: "pearsonStat"

allowable values: "pvalue", "pearsonStat", or "degreesFreedom"
```

Correlation Detection between a feature value and the label value is capable of 3 supported modes:
* Pearson Correlation ("pearsonStat")
> > Calculates the Pearson Correlation Coefficient in range of {-1.0, 1.0}
* Degrees Freedom ("degreesFreedom") 

    Additional Reading: 

    [Reduced Chi-squared statistic](https://en.wikipedia.org/wiki/Reduced_chi-squared_statistic)

    [Generalized Chi-squared distribution](https://en.wikipedia.org/wiki/Generalized_chi-squared_distribution)
    
    [Degrees Of Freedom](https://en.wikipedia.org/wiki/Degrees_of_freedom_(statistics)#Of_random_vectors)
  
> > Calculates the Degrees of Freedom of the underlying linear subspace, in unbounded range {0, n} 
> > where n is the feature vector size.

*Before Overriding this value, ensure that a thorough understanding of this statistic is achieved.*

* p-value ("pvalue")

> > Calculates the p-value of independence from the Pearson chi-squared test in range of {0.0, 1.0}

###### Pearson Filter Direction
```text
Default: "greater"

allowable values: "greater" or "lesser"
```

Specifies whether to filter values out that are higher or lower than the target cutoff value.

###### Pearson Filter Manual Value
```text
Default: 0.0

(placeholder value)
```

Allows for manually filtering based on a hard-defined limit.

> Note: if using "manual" mode on this module, it is *imperative* to provide a valid value through this setter.

###### Pearson Filter Mode
```text
Default: "auto"

allowable values: "auto" or "manual"
```
Determines whether a manual filter value is used as a threshold, or whether a quantile-based approach (automated) 
based on the distribution of Chi-squared test results will be used to determine the threshold.

> The automated approach (using a specified N Tile) will adapt to more general problems and is recommended for getting 
measures of modeling feasibility (exploratory phase of modeling).  However, if utilizing the high-level API with a 
well-understood data set, it is recommended to override the mode, setting it to manual, and utilizing a known
acceptable threshold value for the test that is deemed acceptable.

###### Pearson Auto Filter N Tile
```text
Default: 0.75

allowable range: 0.0 > x > 1.0
```
([Q3 / Upper IQR value](https://en.wikipedia.org/wiki/Interquartile_range))


When in "auto" mode, this will reduce the feature vector by 75% of its total size, retaining only the 25% most 
important predictive-power features of the vector.

### Scaling

The Scaling Module provides for an automated way to set scaling on the feature vector prior to modeling.  
There are a number of ML algorithms that dramatically benefit from scaling of the features to prevent overfitting.  
Although tree-based algorithms are generally resilient to this issue, if using any other family of model, it is 
**highly recommended** to use some form of scaling.

The available scaling modes are:
* [minMax](http://spark.apache.org/docs/latest/ml-features.html#minmaxscaler)
   * Scales the feature vector to the specified min and max.  
   * Creates a Dense Vector.
* [standard](http://spark.apache.org/docs/latest/ml-features.html#standardscaler)
    * Scales the feature vector to the unit standard deviation of the feature (has options for centering around mean) 
    * If centering around mean, creates a Dense Vector.  Otherwise, it can maintain sparsity.
* [normalize](http://spark.apache.org/docs/latest/ml-features.html#normalizer)
    * Scales the feature vector to a p-norm normalization value.
* [maxAbs](http://spark.apache.org/docs/latest/ml-features.html#maxabsscaler)
    * Scales the feature vector to a range of {-1, 1} by dividing each value by the Max Absolute Value of the feature.
    * Retains Vector type.


##### Options
```text
member of configuration case class ScalingConfig()

setters:
.setScalerType("normalize")
.setScalerMin(0.0)                 
.setScalerMax(1.0)
.setPNorm(3)        
.setStandardScalerMeanFlagOff()   |  .setStandardScalerMeanFlagOn()  
.setStandardScalerStdDevFlagOff() |  .setStandardScalerStdDevFlagOn()
```

###### Scaler Type
```text
Default: "minMax"

allowable values: "minMax", "standard", "normalize", or "maxAbs"
```

Sets the scaling library to be employed in scaling the feature vector.

###### Scaler Min
```text
Default: 0.0

Only used in "minMax" mode
```

Used to set the scaling lower threshold for MinMax Scaler 
(normalizes all features in the vector to set the minimum post-processed value specified in this setter)

###### Scaler Max
```text
Default: 1.0

Only used in "minMax" mode
```

Used to set the scaling upper threshold for MinMax Scaler
(normalizes all features in the vector to set the maximum post-processed value specified in this setter)

###### P Norm
```text
Default: 2.0

Only used in "normalize" mode.
```

> NOTE: value must be >=1.0 for proper functionality in a finite vector space.

Sets the level of "smoothing" for scaling the noise out of the vector.  

Further Reading: [P-Norm](https://en.wikipedia.org/wiki/Norm_(mathematics)#p-norm), [L<sup>p</sup> Space](https://en.wikipedia.org/wiki/Lp_space)

###### Standard Scaler Mean Flag
```text
Default: false

Only used in "standard" mode
```

With this flag set to `true`, The features within the vector are centered around mean (0 adjusted) before scaling.
> Read the [docs](http://spark.apache.org/docs/latest/ml-features.html#standardscaler) before switching this on.
> > Setting to 'on' will create a dense vector, which will increase memory footprint of the data set.
###### Standard Scaler StdDev Flag
```text
Default: true

Only used in "standard" mode
```

Scales the data to the unit standard deviation. [Explanation](https://en.wikipedia.org/wiki/Standard_deviation#Corrected_sample_standard_deviation)


## AutoML (Hyper-parameter concurrent genetic tuning)

The implementation for hyper parameter tuning in Providentia is through the use of a genetic algorithm.  
There are currently two different modes: **Batch Mode** and **Continuous Mode**.

#### Generic Configurations

###### K Fold

This setting determines the number of splits that are done with the train / test data sets, minimizing the chance of 
overfitting during training and validation.
```text
Default: 5
```
> It is highly advised to not set this lower than 3.

> [WARNING] If using "chronological" mode with `.setTrainSplitMethod()`, ensure that 
`.setTrainSplitChronologicalRandomPercentage()` is overridden from its default of 0.0.

###### Train Portion

Sets the proportion of the input DataFrame to be used for Train (the value of this variable) and Test 
(1 - the value of this variable)
```text
Default: 0.8
```

###### Train Split Method

This setting allows for specifying how to split the provided data set into test/training sets for scoring validation.
Some ML use cases are highly time-dependent, even in traditional ML algorithms (i.e. predicting customer churn).  
As such, it is important to be able to predict on apriori data and synthetically 'predict the future' by doing validation
testing on a holdout data set that is more recent than the training data used to build the model.

Setter: `.setTrainSplitMethod(<String>)`
```text
Default: "random"

Available options: "random" or "chronological"
```
> Chronological split method **does not require** a date type or datetime type field. Any sort-able / continuous distributed field will work.

> Leaving the default value of "random" will randomly shuffle the train and test data sets each k-fold iteration.

###### Train Split Chronological Column

Specify the field to be used in restricting the train / test split based on sort order and percentage of data set to conduct the split.
> As specified above, there is no requirement that this field be a date or datetime type.  However, it *is recommended*.

Setter: `.setTrainSplitChronologicalColumn(<String>)`

```text
Default: "datetime"

This is a placeholder value.
Validation will occur when modeling begins (post data-prep) to ensure that this field exists in the data set.
```
> It is ***imperative*** that this field exists in the raw DataFrame being supplied to the main class. ***CASE SENSITIVE MATCH***
> > Failing to ensure this setting is correctly applied could result in an exception being thrown mid-run, wasting time and resources.

###### Train Split Chronological Random Percentage

Due to the fact that a Chronological split, when done by a sort and percentage 'take' of the DataFrame, each k-fold 
generation would extract an identical train and test data set each iteration if the split were left static.  This
setting allows for a 'jitter' to the train / test boundary to ensure that k-fold validation provides more useful results.

Setter: `.setTrainSplitChronologicalRandomPercentage(<Double>)` representing the ***percentage value*** (fractional * 100)
```text
Default: 0.0

This is a placeholder value.
```
> [WARNING] Failing to override this value if using "chronological" mode on `.setTrainSplitMethod()` is equivalent to setting 
`.setKFold(1)` for efficacy purposes, and will simply waste resources by fitting multiple copies of the same hyper 
parameters on the exact same data set.

###### First Generation Gene Pool
Determines the random search seed pool for the genetic algorithm to operate from.  
There are space constraints on numeric hyper parameters, character, and boolean that are distinct for each modeling 
family and model type.
Setting this value higher increases the chances of minimizing convergence, at the expense of a longer run time.

> Setting this value below 10 is ***not recommended***.  Values less than 6 are not permitted and will throw an assertion exception.

Setter: `.setFirstGenerationGenePool(<Int>)`

```text
Default: 20
```

###### Seed

Sets the seed for both random selection generation for the initial random pool of values, as well as initializing 
another randomizer for random train/test splits.

Setter: `.setSeed(<Long>)`

```text
Default: 42L

```

###### Evolution Strategy
Determining the mode (batch vs. continuous) is done through setting the parameter `.setEvolutionStrategy(<String>)`

```text
Default: "batch"

Available options: "batch" or "continuous"
```
> Sets the mutation methodology used for optimizing the hyper parameters for the model.

### Batch Mode

In batch mode, the hyper parameter space is explored with an initial seed pool (based on Random Search with constraints).

After this initial pool is evaluated (in parallel), the best n parents from this seed generation are used to 'sire' a new generation.

This continues for as many generations are specified through the config `.setNumberOfGenerations(<Int>)`, 
*or until a stopping threshold is reached at the conclusion of a concurrent generation batch run.*

#### Batch-Specific Configurations

###### Parallelism
Sets the number of concurrent models that will be evaluated in parallel through Futures.  
This creates a new [ForkJoinPool](https://java-8-tips.readthedocs.io/en/stable/forkjoin.html), and as such, it is important to not set it too high, in order to prevent overloading
the driver JVM with too many elements in the `Array[DEqueue[task]]` ForkJoinPool.  
NOTE: There is a global limit of 32767 threads on the JVM, as well.
However, in practice, running too many models in parallel will likely OOM the workers anyway.  

>Recommended is in the {4, 500} range, depending on the model and size of the test/training sets.

Setter: `.setParallelism(<Int>)`

```text
Default: 20
```

###### Number of Generations

This setting, applied only to batch processing mode, sets the number of mutation generations that will occur.

> The higher this number, the better the exploration of the hyper parameter space will occur, although it comes at the 
expense of longer run-time.  This is a *sequential blocking* setting.  Parallelism does not effect this.

Setter: `.setNumberOfGenerations(<Int>)`

```text
Default: 10
```

###### Number of Parents To Retain

This setting will restrict the number of candidate 'best' results of the previous generation of hyper parameter tuning,
using these result's configuration to mutate the next generation of attempts.

> The higher this setting, the more 'space exploration' that will occur.  However, it may slow the possibility of 
converging to an optimal condition.

Setter: `.setNumberOfParentsToRetain(<Int>)`

```text
Default: 3
```

###### Number of Mutations Per Generation

This setting specifies the size of each evolution batch pool per generation (other than the first seed generation).

> The higher this setting is set, the more alternative spaces are checked, however, if this value is higher than
what is set by `.setParallelism()`, it will add to the run-time.

Setter: `.setNumberOfMutationsPerGeneration(<Int>)`

```text
Default: 10
```

###### Genetic Mixing

Setter

```text
Default: 
```

###### Generational Mutation Strategy

Setter

```text
Default: 
```

###### Fixed Mutation Value

Setter

```text
Default: 
```

###### Mutation Magnitude Mode

Setter

```text
Default: 
```

###### Auto Stopping Flag

Setter

```text
Default: 
```

###### Auto Stopping Score

Setter

```text
Default: 
```

### Continuous Mode


#### Continuous-Specific Configurations

###### Continuous Evolution Max Iterations

Setter

```text
Default: 
```

###### Continuous Evolution Stopping Score

Setter

```text
Default: 
```

###### Continuous Evolution Parallelism

Setter

```text
Default: 
```

###### Continuous Evolution Mutation Aggressiveness

Setter

```text
Default: 
```

###### Continuous Evolution Genetic Mixing

Setter

```text
Default: 
```

###### Continuous Evolution Rolling Improvement Count

[EXPERIMENTAL]
This is an early stopping criteria that measures the cumulative gain of the score as the job is running.  
If improvements ***stop happening***, then the continuous iteration of tuning will stop to prevent useless continuation.

Setter: `.`

```text
Default: 
```

### Model Family Specific Settings


Setters (for all numeric boundaries): `.setNumericBoundaries(<Map[String, Tuple2[Double, Double]]>)`

Setters (for all string boundaries): `.setStringBoundaries(<Map[String, List[String]]>)`

#### Random Forest

###### Default Numeric Boundaries

###### Default String Boundaries

#### Gradient Boosted Trees

###### Default Numeric Boundaries

###### Default String Boundaries

#### Decision Trees

###### Default Numeric Boundaries

###### Default String Boundaries

#### Linear Regression

###### Default Numeric Boundaries

###### Default String Boundaries

#### Logistic Regression 

###### Default Numeric Boundaries

###### Default String Boundaries

#### Multilayer Perceptron Classifier

###### Default Numeric Boundaries

###### Default String Boundaries

#### Linear Support Vector Machines

###### Default Numeric Boundaries

###### Default String Boundaries




## Feature Importance

## Decision Splits

