package squantlib.database

import squantlib.model.discountcurve.DiscountCurveFactory
import squantlib.database.schemadefinitions.{ Bond => dbBond, BondPrice, RateFXParameter}
import squantlib.database.objectconstructor._
import squantlib.database.QLConstructors._
import squantlib.model.timeseries.TsAnalysis._
import squantlib.setting.PricingConvention
import org.squeryl.PrimitiveTypeMode._
import org.jquantlib.instruments.{Bond => QLBond}
import org.jquantlib.time.{Date => qlDate, Frequency, TimeSeries}
import org.jquantlib.pricingengines.PricingEngine
import org.jquantlib.termstructures.YieldTermStructure
import java.lang.{Double => JavaDouble}

/**
* Functions to directly access database to retrieve Jquantlib or Squantlib objects.
*/
object QLDB {
  
   /**
    * Returns discount curve factory.
    */
	def getDiscountCurveFactory(paramset:String):Option[DiscountCurveFactory] = {
	  val ratefxparameters:Set[RateFXParameter] = DB.getRateFXParameters(paramset)
	  val discountcurves = ratefxparameters.toDiscountCurves 
	  val cdscurves = DB.getCDSParameters(paramset).toCDSCurves
	  
	  if (discountcurves.size == 0 || cdscurves.size == 0) None
	  else Some(new DiscountCurveFactory(
		    discountcurves.map(c => (c.currency.code, c)).toMap, 
		    cdscurves.map(c => (c.issuerid, c)).toMap, 
		    paramset))
	}
	
	def getDiscountCurveFactoryFromInputParameter(paramset:String):DiscountCurveFactory = {
	  val ratefxparameters:Set[RateFXParameter] = DB.getRateFXParameters(paramset)
	  val discountcurves = ratefxparameters.toDiscountCurves 
	  val cdscurves = DB.getCDSParameters(paramset).toCDSCurves
	  
	  new DiscountCurveFactory(
	    discountcurves.map(c => (c.currency.code, c)).toMap, 
	    cdscurves.map(c => (c.issuerid, c)).toMap, 
	    paramset)
	}
	
   /**
    * Returns jquantlib Bond object. 
    * 
    * @param id Identifications of the target bonds
    * @param factory Optional. Providing discount curve factory would automatically set discounting bond engine as default pricing engine (where applicable)
    */
	def getBond(id:String):Option[QLBond] = {
	  val bonds = getBonds(Set(id))
	  if (bonds.isEmpty) None else Some(bonds.head)
	}
	
	def getBond(id:String, factory:DiscountCurveFactory):Option[QLBond] = {
	  val bonds = getBonds(Set(id), factory)
	  if (bonds.isEmpty) None else Some(bonds.head)
	}
	
	def getBonds:Set[QLBond] = bondsConstructor(DB.getBonds, null)
	def getBonds(bonds:Set[dbBond]):Set[QLBond] = bondsConstructor(bonds, null)
	def getBonds(id:Traversable[String]):Set[QLBond] = bondsConstructor(DB.getBonds(id), null)
	def getBonds(factory:DiscountCurveFactory):Set[QLBond] = bondsConstructor(DB.getBonds, factory)
	def getBonds(id:Traversable[String], factory:DiscountCurveFactory):Set[QLBond] = bondsConstructor(DB.getBonds(id), factory)
	def getBonds(bonds:Set[dbBond], factory:DiscountCurveFactory):Set[QLBond] = bondsConstructor(bonds, factory)
	
	def getBonds(ids:Traversable[String], builder:dbBond => Option[QLBond], pricingengine:QLBond => PricingEngine, valuedate:qlDate):Set[QLBond] = {
		val qlbonds:Set[QLBond] = DB.getBonds(ids).map(builder).flatMap(b => b)
		qlbonds.foreach(b => b.setPricingEngine(pricingengine(b), valuedate))
		qlbonds
	}
	
	def bondsConstructor(dbbonds:Set[dbBond], factory:DiscountCurveFactory):Set[QLBond] = 
	  dbbonds.map(b => PricingConvention.bondConstructor(b, factory)).flatMap(b => b)
	
   /**
    * Returns time series for non-FX input parameters.
    * @param fromDate A starting point of Date. Range includes this date.
    * @param toDate End point of Date. Range includes this date.
    * @param instrument Parameter type, such as "SWAP", "CASH" or "BASISSWAP"
    * @param asset Underlying asset, usually a currency
    * @param maturity Maturity of the target rate, such as "5Y" or "6M"
    */
	def getRateFXTimeSeries(fromDate:qlDate, toDate:qlDate, instrument:String, asset:String, maturity:String):TimeSeries[JavaDouble] = 
	  DB.getRateFXTimeSeries(fromDate.longDate, toDate.longDate, instrument, asset, maturity).toTimeSeries
	
   /**
    * Returns time series for FX parameters.
    * @param fromDate A starting point of Date. Range includes this date.
    * @param toDate End point of Date. Range includes this date.
    * @param fx1 Currency to be measured
    * @param fx2 Measuring currency.
    * 		 For example, fx1 = "USD", fx2 = "JPY" would provide number of JPY per 1 USD = around 80.00
    */
	def getFXTimeSeries(fromDate:qlDate, toDate:qlDate, fx1:String, fx2:String):TimeSeries[JavaDouble] = 
	  DB.getFXTimeSeries(fromDate.longDate, toDate.longDate, fx1, fx2).toTimeSeries
	  
   /**
    * Returns time series for CDS parameters.
    * @param fromDate A starting point of Date. Range includes this date.
    * @param toDate End point of Date. Range includes this date.
    * @param currencyid Currency of the CDS quotation
    * @param issuer Target issuer
    * @param maturity CDS maturity
    */
	def getCDSTimeSeries(fromDate:qlDate, toDate:qlDate, currencyid:String, issuerid:String, maturity:String):TimeSeries[JavaDouble] = 
	  DB.getCDSTimeSeries(fromDate.longDate, toDate.longDate, currencyid, issuerid, maturity).toTimeSeries
	  
}


