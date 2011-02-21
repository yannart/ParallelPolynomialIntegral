/**
 * Copyright (C) 2011 Yann Nicolas.
 */

package com.yannart.akka.integralcalculator

import scala.collection.mutable.ListBuffer
import scala.math._

/**
 * Utils to work with polynomials.
 */
object PolynomialUtils {
	
	/**
	 * Calculates f(x) of the polynomial using the coefficient values.
	 * @param x x value.
	 * @param coefList list of coefficients values.
	 * @return f(x) of the polynomial for x value.
	 */
	def f(x: Double, coefList : List[Double]) : Double = {
		var f : Double = 0
		
		for (i <- 0 to coefList.size - 1) {
			f += coefList(i) * pow(x , i)
		}
		return f
	}
	
	/**
	 * Calculates the coefficients of the integral of a polynomial.
	 * @param coefList list of coefficients values of the polynomial.
	 * @return coefficients of the integral of the polynomial.
	 */
	def integral(coefList : List[Double]) : List[Double] = {
		val integralCoefList : ListBuffer[Double] = new ListBuffer[Double]
		
		//first coef is empty
		integralCoefList += 0
		
		for (i <- 0 to coefList.size - 1) {
			integralCoefList += coefList(i) / (i + 1)
		}
		
		integralCoefList.toList
	}
	
	/**
	 * Calculates the area under the curve for a polynomial between x1 and x2.
	 * @param coefList list of coefficients values of the polynomial.
	 * @param x1 first value of the interval.
	 * @param x2 last value of the interval.
	 * @return Area under the curve of the polynomial for the interval.
	 */
	def areaUnderTheCurve(coefList : List[Double], x1: Double, x2: Double) : Double = {

		//Simpson formula
		val fx1 = PolynomialUtils.f(x1, coefList)
		val fx2 = PolynomialUtils.f(x2, coefList)
		val fxm = PolynomialUtils.f((x1 + x2)/2, coefList)
		(x2 - x1) / 6 * ( fx1 + 4 * fxm + fx2 )
	}
	
	/**
	 * Creates the String representation of a polynomial.
	 * @param coefList list of coefficients values of the polynomial.
	 * @return String representation of the polynomial.
	 */
	def polynomialToString(coefList : List[Double]) : String = {
    	val sb : StringBuilder = new StringBuilder
    	var first = true
    	for (i <- 0 to coefList.size - 1) {
    			
    		//Print the coef only if it is not 0
    		if(coefList(i) != 0) {
    			if(coefList(i) > 0) {
    				if(!first) sb.append(" + ")
    			} else {
    				sb.append(" - ")
    			}
    			
    			//if the coefficient is not 1 or if it has grade 0, it is printed
    			if(abs(coefList(i)) != 1 || i == 0) {
    				sb.append(abs(coefList(i)))
    			}
    			
    			if(i > 0) sb.append("X")
    			if(i > 1) {
    				sb.append("^")
    				sb.append(i)
    			}
    				
    			first = false
    		}
    	}
    	
    	return sb.toString
	}
}
