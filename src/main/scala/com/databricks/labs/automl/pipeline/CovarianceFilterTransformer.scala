package com.databricks.labs.automl.pipeline

import com.databricks.labs.automl.sanitize.FeatureCorrelationDetection
import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.param.{DoubleParam, ParamMap}
import org.apache.spark.ml.util.{DefaultParamsReadable, DefaultParamsWritable, Identifiable}
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, Dataset}

class CovarianceFilterTransformer(override val uid: String)
  extends Transformer
    with DefaultParamsWritable
    with HasLabelColumn
    with HasFeaturesColumns
    with HasFieldsRemoved
    with HasTransformCalculated {

  private val logger: Logger = Logger.getLogger(this.getClass)

  def this() = this(Identifiable.randomUID("CovarianceFilterTransformer"))

  final val correlationCutoffLow: DoubleParam = new DoubleParam(this, "correlationCutoffLow", "correlationCutoffLow")

  final val correlationCutoffHigh: DoubleParam = new DoubleParam(this, "correlationCutoffHigh", "correlationCutoffHigh")

  def setCorrelationCutoffLow(value: Double): this.type = set(correlationCutoffLow, value)

  def getCorrelationCutoffLow: Double = $(correlationCutoffLow)

  def setCorrelationCutoffHigh(value: Double): this.type = set(correlationCutoffHigh, value)

  def getCorrelationCutoffHigh: Double = $(correlationCutoffHigh)


  override def transform(dataset: Dataset[_]): DataFrame = {
    // Output has no feature vector

    if(!getTransformCalculated) {
      val covarianceFilteredData =
        new FeatureCorrelationDetection(dataset.toDF(), getFeatureColumns)
          .setLabelCol(getLabelColumn)
          .setCorrelationCutoffLow(getCorrelationCutoffLow)
          .setCorrelationCutoffHigh(getCorrelationCutoffHigh)
          .filterFeatureCorrelation()

      setFieldsRemoved(getFeatureColumns.filterNot(field => covarianceFilteredData.columns.contains(field)))
      setTransformCalculated(true)
      val covarianceFilterLog =
        s"Covariance Filtering completed.\n  Removed fields: ${getFieldsRemoved.mkString(", ")}"

      logger.log(Level.INFO, covarianceFilterLog)
      println(covarianceFilterLog)
      transformSchema(dataset.schema)
      covarianceFilteredData
    } else {
      transformSchema(dataset.schema)
      dataset.drop(getFieldsRemoved:_*)
    }
  }

  override def transformSchema(schema: StructType): StructType = {
    StructType(schema.fields.filterNot(field => getFieldsRemoved.contains(field.name)))
  }

  override def copy(extra: ParamMap): CovarianceFilterTransformer = defaultCopy(extra)

}

object CovarianceFilterTransformer extends DefaultParamsReadable[CovarianceFilterTransformer] {

  override def load(path: String): CovarianceFilterTransformer = super.load(path)

}