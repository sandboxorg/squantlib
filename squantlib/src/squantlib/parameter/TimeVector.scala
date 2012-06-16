package squantlib.parameter

import scala.collection.immutable.TreeMap
import scala.collection.immutable.SortedMap
import scala.collection.mutable.MutableList

import scala.collection.{Iterable, Iterator}

import org.jquantlib.time.{ Date => JDate }
import org.jquantlib.time.{ Period => JPeriod }
import org.jquantlib.time.TimeUnit
import org.jquantlib.daycounters._
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction
import org.apache.commons.math3.analysis.interpolation._
import org.apache.commons.math3.analysis.function.Log
import org.apache.commons.math3.analysis.function.Exp

/**
 * Encapsulate time series vector parameter with interpolation, extrapolation and other adjustments functions.
 * 
 */
trait TimeVector extends Iterable[Pair[JDate, Double]] {
	/**
	 * Returns base date of this vector. 
	 */
	var valuedate : JDate
	/**
	 * Returns number of days between value date and first defined point.
	 * This point is the low boundary between interpolation & extrapolation.
	 */
    val mindays : Long
	/**
	 * Returns number of days between value date and final defined point. 
	 * This point is the high boundary between interpolation & extrapolation.
	 */
    val maxdays : Long
	/**
	 * Returns date of final defined point. 
	 * This point is the high boundary between interpolation & extrapolation.
	 */
	val maxdate : JDate
	/**
	 * Returns period between valueDate and final defined point. 
	 * This point is the high boundary between interpolation & extrapolation.
	 */
	val maxperiod: JPeriod
	/**
	 * Returns the value corresponding to the given date.
	 * @param observation date as the number of calendar days after value date.
	 */
    def value(days : Long) : Double
	/**
	 * Returns the value corresponding to the given date.
	 * @param observation date as day count fraction and its day count method.
	 */
    def value(dayfrac : Double, dayCounter:DayCounter) : Double = value((dayfrac * 365 / dayCounter.annualDayCount).toLong)
	/**
	 * Returns the value corresponding to the given date.
	 * @param observation date
	 */
    def value(date : JDate) : Double = value(date.serialNumber() - valuedate.serialNumber())
	/**
	 * Returns the value corresponding to the given date.
	 * @param observation date as the period from value date.
	 */
    def value(period : JPeriod) : Double = value(period.days(valuedate))
  /**
   * Returns an Iterator that provides data during mindays..maxdays incremented by 1 day
   */
    def iterator:Iterator[Pair[JDate, Double]] = {
      // FIXME: This could be inefficient.
      val list = MutableList[Pair[JDate, Double]]()
      for (i <- mindays to maxdays)
        list += Pair(valuedate.add(i.toInt), value(i)) // .toInt, srsly?
      return list.iterator
    }
  /**
   * Returns a String representation of this object.
   */
    override def toString:String = {
      getClass + " (" + valuedate.add(mindays.toInt) + " to " + valuedate.add(maxdays.toInt) + ")"
    }
}

/**
 * Basic Framework for Long-Double Interpolation
 * Points are interpolated between max and min range, and extrapolated outside.
 */
trait AbstractTimeVector {
	var valuedate : JDate
	val mindays : Long
	val maxdays : Long

	def lowextrapolation(v : Long) : Double
    def highextrapolation(v : Long) : Double
    def interpolation(v : Long) : Double
  
    def value(v : Long) : Double = {
      v match {
        case vv if vv <= mindays => lowextrapolation(vv)
        case vv if vv >= maxdays => highextrapolation(vv)
        case _ => interpolation(v)
          }
    }
}

/**
 * TimeVector with Spline interpolation and no extrapolation. (ie. y-value is constant after last date)
 * Date is internally described as number of days since value date, but can be accessed by Date or Period.
 * 
 * @constructor constructs polynomial curve from given points. extra flat points are added after final date to manage overshoot.
 * @param input points
 * @param number of extra points (optional)
 */
class SplineNoExtrapolation(var valuedate : JDate, inputvalues:SortedMap[JPeriod, Double], extrapoints:Int) extends TimeVector with AbstractTimeVector {
	require(inputvalues.size >= 3)
	
    val splinefunction = {
	    var inputpoints :TreeMap[Long, Double] = TreeMap.empty
	    for (d <- inputvalues.keySet) { inputpoints ++= Map(d.days(valuedate) -> inputvalues(d)) }
	    for (i <- 1 to extrapoints) { inputpoints ++= Map((inputvalues.lastKey.days(valuedate) + (30L * i.toLong)) -> inputvalues.last._2) }
	    val keysarray = inputpoints.keySet.toArray
	    val valarray = keysarray.map((i:Long) => inputpoints(i))
	    new SplineInterpolator().interpolate(keysarray.map((i:Long)=>i.toDouble), valarray)
    }
    
	val mindays = inputvalues.firstKey.days(valuedate)
	val maxdays = inputvalues.lastKey.days(valuedate)
	val maxdate = new JDate(valuedate.serialNumber() + maxdays)
	val maxperiod = new JPeriod(maxdays.toInt, TimeUnit.Days)

	def lowextrapolation(v : Long) = inputvalues.first._2
    def highextrapolation(v : Long) = inputvalues.last._2
    def interpolation(v : Long) = splinefunction.value(v.toDouble)
}

/**
 * TimeVector with Spline interpolation and exponential extrapolation. (always > 0)
 * Date is internally described as number of days since value date, but can be accessed by Date or Period.
 * 
 * @constructor constructs polynomial curve from given points. extra flat points are added after final date to manage overshoot.
 * @param input points
 * @param number of extra points (optional)
 */
class SplineEExtrapolation(var valuedate : JDate, inputvalues:SortedMap[JPeriod, Double], extrapoints:Int) extends TimeVector with AbstractTimeVector {
	require(inputvalues.size >= 3)
	
	val firstvalue = inputvalues.first._2
	val impliedrate = -1.0 * new Log().value(inputvalues.last._2 / firstvalue) / inputvalues.lastKey.days(valuedate).toDouble
	val extrapolator = new Exp()
	
	val mindays = inputvalues.firstKey.days(valuedate)
	val maxdays = inputvalues.lastKey.days(valuedate)
	val maxdate = new JDate(valuedate.serialNumber() + maxdays)
	val maxperiod = new JPeriod(maxdays.toInt, TimeUnit.Days)

	def lowextrapolation(v : Long) = inputvalues.first._2
    def highextrapolation(v : Long) = firstvalue * extrapolator.value(-1.0 * impliedrate * v.toDouble)
    def interpolation(v : Long) = splinefunction.value(v.toDouble)

    val splinefunction = {
		var inputpoints :TreeMap[Long, Double] = TreeMap.empty
		for (d <- inputvalues.keySet) { inputpoints ++= Map(d.days(valuedate) -> inputvalues(d)) }
	    for (i <- 1 to extrapoints; d = inputvalues.lastKey.days(valuedate) + (30L * i.toLong)) { inputpoints ++= Map(d -> highextrapolation(d)) }
		val keysarray = inputpoints.keySet.toArray
		val valarray = keysarray.map((i:Long) => inputpoints(i))
		new SplineInterpolator().interpolate(keysarray.map((i:Long)=>i.toDouble), valarray)
    }
}

/**
 * TimeVector with Linear interpolation and no extrapolation. (ie. y-value is constant after last date)
 * Date is internally described as number of days since value date, but can be accessed by Date or Period.
 * 
 * @constructor constructs linear curve from given points. extra flat points are added after final date to manage overshoot.
 * @param input points
 */
class LinearNoExtrapolation(var valuedate : JDate, inputvalues:SortedMap[JPeriod, Double]) extends TimeVector with AbstractTimeVector {
	require(inputvalues.size >= 2)
  
    val linearfunction = {
	    var inputpoints :TreeMap[Long, Double] = TreeMap.empty
	    for (d <- inputvalues.keySet) { inputpoints ++= Map(d.days(valuedate) -> inputvalues(d)) }
	    val keysarray = inputpoints.keySet.toArray
	    val valarray = keysarray.map((i:Long) => inputpoints(i))
	    new LinearInterpolator().interpolate(keysarray.map((i:Long)=>i.toDouble), valarray)    
	}
	
	val mindays = inputvalues.firstKey.days(valuedate)
	val maxdays = inputvalues.lastKey.days(valuedate)
	val maxdate = new JDate(valuedate.serialNumber() + maxdays)
	val maxperiod = new JPeriod(maxdays.toInt, TimeUnit.Days)

	def lowextrapolation(v : Long) = inputvalues.first._2
    def highextrapolation(v : Long) = inputvalues.last._2
    def interpolation(v : Long) = linearfunction.value(v.toDouble)
}

/**
 * Flat vector
 * 
 * @param input point
 */
class FlatVector(var valuedate : JDate, inputvalues:Map[JPeriod, Double]) extends TimeVector with AbstractTimeVector {
	require(inputvalues.size == 1)
	
	val constantvalue = inputvalues.first._2
	val firstvalue = inputvalues.first._2
	
	val mindays = inputvalues.first._1.days(valuedate)
	val maxdays = mindays
	val maxdate = new JDate(valuedate.serialNumber() + maxdays)
	val maxperiod = new JPeriod(maxdays.toInt, TimeUnit.Days)

	def lowextrapolation(v : Long) = constantvalue
    def highextrapolation(v : Long) = constantvalue
    def interpolation(v : Long) = constantvalue
}
