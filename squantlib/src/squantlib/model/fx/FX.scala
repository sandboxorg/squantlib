package squantlib.model.fx

import squantlib.model.discountcurve.DiscountCurve
import org.jquantlib.currencies.Currency
import org.jquantlib.daycounters.DayCounter
import org.jquantlib.time.{Date => qlDate, Period => qlPeriod}

/**
 * Basic FX framework providing spot, forward and volatility
 * 
 * @constructor stores each information
 * @param float index, daycount & payment frequency for fixed leg
 */
trait FX {
  
	val curve1:DiscountCurve
	val currency1:Currency = curve1.currency
	
	val curve2:DiscountCurve 
	val currency2:Currency = curve2.currency
	
	require (curve1.valuedate eq curve2.valuedate)
	val valuedate = curve1.valuedate
	
	val name = currency1.code + currency2.code
	
	/**
	 * Returns FX spot rate
	 */
	val spot:Double = curve2.fx / curve1.fx
	
	/**
	 * Returns the volatility corresponding to the given date & strike.
	 * @param days observation date as the number of calendar days after value date.
	 * @param strike fx strike
	 */
	def volatility(days:Long, strike:Double):Double
	
	/**
	 * Returns the volatility corresponding to the given date & strike.
	 * @param observation date as day count fraction and its day count method.
	 * @param strike fx strike
	 */
	def volatility(dayfrac:Double, dayCounter:DayCounter, strike:Double):Double = volatility(toDays(dayfrac, dayCounter), strike)	
	
	/**
	 * Returns the volatility corresponding to the given date & strike.
	 * @param observation date
	 * @param strike fx strike
	 */
	def volatility(date:qlDate, strike:Double):Double = volatility(toDays(date), strike)	
	
	/**
	 * Returns the volatility corresponding to the given date & strike.
	 * @param observation date
	 * @param observation date as the period from value date.
	 */
	def volatility(period:qlPeriod, strike:Double):Double = volatility(toDays(period), strike)	
	  
	/**
	 * Returns the value corresponding to the given date.
	 * @param observation date as the number of calendar days after value date.
	 */
    def forwardfx(days : Long) : Double = spot * curve1(days) / curve2(days)
    def zc1(days:Long) = curve1(days)
    def zc2(days:Long) = curve2(days)
    
	/**
	 * Returns the value corresponding to the given date.
	 * @param observation date as day count fraction and its day count method.
	 */
    def forwardfx(dayfrac : Double, dayCounter:DayCounter) : Double = forwardfx(toDays(dayfrac, dayCounter))
    def zc1(dayfrac:Double, dayCounter:DayCounter) = curve1(toDays(dayfrac, dayCounter))
    def zc2(dayfrac:Double, dayCounter:DayCounter) = curve2(toDays(dayfrac, dayCounter))
    
	/**
	 * Returns the value corresponding to the given date.
	 * @param observation date
	 */
    def forwardfx(date : qlDate) : Double = forwardfx(toDays(date))
    def zc1(date:qlDate) = curve1(toDays(date))
    def zc2(date:qlDate) = curve2(toDays(date))
    
	/**
	 * Returns the value corresponding to the given date.
	 * @param observation date as the period from value date.
	 */
    def forwardfx(period : qlPeriod) : Double = forwardfx(toDays(period))
    def zc1(period : qlPeriod) = curve1(toDays(period))
    def zc2(period : qlPeriod) = curve2(toDays(period))
    
    def maxdays = Math.min(curve1.maxdays, curve2.maxdays)
	
	/**
	 * Private date conversion functions
	 */
    private def toDays(dayfrac:Double, dayCounter:DayCounter) = (dayfrac * 365 / dayCounter.annualDayCount).toLong
    private def toDays(date:qlDate) = date.serialNumber() - valuedate.serialNumber()
    private def toDays(period:qlPeriod) = period.days(valuedate)
    
} 


class FX_novol(val curve1:DiscountCurve, val curve2:DiscountCurve) extends FX {
	def volatility(days:Long, strike:Double):Double = Double.NaN
}

class FX_flatvol(val curve1:DiscountCurve, val curve2:DiscountCurve, vol:Double) extends FX {
	def volatility(days:Long, strike:Double):Double = vol
}

class FX_nosmile(val curve1:DiscountCurve, val curve2:DiscountCurve, vol:Long => Double) extends FX {
	def volatility(days:Long, strike:Double):Double = vol(days)
}

class FX_smiled(val curve1:DiscountCurve, val curve2:DiscountCurve, vol:(Long, Double) => Double) extends FX {
	def volatility(days:Long, strike:Double):Double = vol(days, strike)
}
