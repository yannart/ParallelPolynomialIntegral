/**
 * Copyright (C) 2011 Yann Nicolas.
 */

package com.yannart.akka.integralcalculator

import akka.actor.{Actor, ActorRef, ActorRegistry }
import akka.dispatch.CompletableFuture
import akka.stm._
import akka.util.Logging
import Actor._
import com.yannart.akka.integralcalculator.PolynomialUtils._
import java.util.Date
import scala.math.{min, abs}

/******************************************************************************
Akka Integral Calculator
******************************************************************************/

/**
 * Messages passed to actors.
 */
case class Range(from: Double, to: Double)
case class PolynomialRange(range: Range, coefList : List[Double])
case class ComputationContext(range: Range, coefList : List[Double], precision: Double)
case class RemoteActor(id: String, host: String, port : Int)


object Config {
	val serverHost = "localhost"
	val serverId = "server"
	val serverPort = 2553
	val satelliteHost = "localhost"
	val satelliteId = "satellite"
	val satellitePort = 2552
}

class PolynomialIntegralRemoteDelegateActor extends Actor {
	
	var mainSenderFuture : CompletableFuture[Any] = null
	
	def receive = {
    	case computationContext : ComputationContext => 
    	
    		mainSenderFuture = self.senderFuture.get
    		
    		val actor = remote.actorFor(Config.satelliteId, Config.satelliteHost , Config.satellitePort)
    		actor ! computationContext
    		
    	case result : Double =>
    		log.info("Result received from satellite: " + result)
    		mainSenderFuture.completeWithResult(result)
	}	
	
	override def preStart = {
    	Actor.remote.start(Config.serverHost , Config.serverPort)
    	Actor.remote.register(Config.serverId, self)
	}
}

class PolynomialIntegralDispatcherActor extends Actor {
	var total : Double = 0
	var activeActors : Int = 0
	var initializationFinished : Boolean = false
	var serverActor : ActorRef = null
	
	def receive = { 
    	case computationContext : ComputationContext => 
    		
    		total = 0
    		activeActors = 0
    		serverActor = remote.actorFor(Config.serverId, Config.serverHost , Config.serverPort)
    		
    		var lastX = computationContext.range.from
    		while (lastX < computationContext.range.to) {
    			
    			val actor = actorOf[PolynomialApproxIntegralCalculatorActor].start
    			val toX = lastX + computationContext.precision
    			activeActors += 1
    			actor ! PolynomialRange (Range(lastX, min(toX, computationContext.range.to)), computationContext.coefList)
    			lastX = toX
    		}
    		initializationFinished = true
    	
    	//Result received from a computer actor
    	case result : Double =>
    		total += result
 		
    		//stops the sender actor
    		self.sender.get.stop
    		
    		activeActors -= 1
    		if(initializationFinished && activeActors <= 0) {
    			serverActor ! total
    		}
	}
	
	override def preStart = {
    	Actor.remote.start(Config.satelliteHost , Config.satellitePort)
    	Actor.remote.register(Config.satelliteId, self)
	}
}



/**
 * Calculates an approximation of a polynomial integral using the Simpson formula rule.
 */
class PolynomialApproxIntegralCalculatorActor extends Actor {
	
	def receive = {
    	case polynomialRange : PolynomialRange => 
    		self.reply(areaUnderTheCurve(polynomialRange.coefList, polynomialRange.range.from , polynomialRange.range.to ))
	}
}

/**
 * Test runner emulating an integral calculation.
 */
object Runner {
	
	def runServer = {
		
		//Configuration
		// - 1 - 4X + 3X^2 - 2X^3 + X^4 + X^5
		val coefList : List[Double] = List(-1, -4, 3, -2, 1, 4)
		val precision : Double = 0.001
		val range = Range(-10, 10)
		val computationContext = ComputationContext(range, coefList, precision)
		
		//Real integral
		val realStartDate = new Date
		val integralCoefList = integral(coefList)
		val integralValue = (f(range.to, integralCoefList) -  f(range.from, integralCoefList))
		val realEndDate = new Date
		
		//Approximation of the integral
		val approxActor = actorOf[PolynomialIntegralRemoteDelegateActor].start
		approxActor.setTimeout(60000)
		
		val approxStartDate = new Date
		val future = approxActor !!! computationContext
		future.awaitBlocking
		val approxIntegralValue = future.result.getOrElse(throw new RuntimeException("TIMEOUT")).asInstanceOf[Double]
		val approxEndDate = new Date
		approxActor.stop
		
		//Messages 
		println("Polynomial equation is " + polynomialToString(coefList))
		println("Integral to be calculated between [ " + range.from + " , " + range.to + " ]")
		println("Approximate integral calculated using steps of " + precision)
		println("Integral equation is " + polynomialToString(integralCoefList))
		print("Real integral value = " + integralValue)
		println(" (calculated in " + (realEndDate.getTime - realStartDate.getTime) + " ms)")
		print("Approximate integral value = " + approxIntegralValue)
		println(" (calculated in " + (approxEndDate.getTime - approxStartDate.getTime) + " ms)")
		println("Error = " + abs((approxIntegralValue - integralValue ) / integralValue * 100) + "%")
		
	}
	
	def runSatellite = {
		 Actor.actorOf[PolynomialIntegralDispatcherActor].start
	}
}
