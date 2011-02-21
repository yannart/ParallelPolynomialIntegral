/**
 * Copyright (C) 2011 Yann Nicolas.
 */

package com.yannart.akka.integralcalculator

import akka.actor.{Actor, ActorRef }
import akka.dispatch.CompletableFuture
import akka.util.Logging
import Actor._
import com.yannart.akka.integralcalculator.PolynomialUtils._
import java.util.Date
import scala.math.{abs, ceil}

/**
 * Messages passed to actors.
 */
case class Interval(from: Double, to: Double)
case class PolynomialInterval(interval: Interval, coefList : List[Double])
case class ComputationContext(interval: Interval, coefList : List[Double], precision: Double)

object Config {
	val clientHost = "localhost"
	val clientId = "client"
	val clientPort = 2553
	val serverHost = "localhost"
	val serverId = "server"
	val serverPort = 2552
}

/**
 * Actor that delegates the calculation of a polynomial integral to a remote Actor.
 * It runs on the client.
 */
class AreaUnderTheCurveRemoteDelegateActor extends Actor {
	
	/** Reference to the future to be filled with the calculation result. */
	var mainSenderFuture : CompletableFuture[Any] = null
	
	def receive = {
    	case computationContext : ComputationContext => 
    	
    		mainSenderFuture = self.senderFuture.get
    		
    		val actor = remote.actorFor(Config.serverId, Config.serverHost , Config.serverPort)
    		actor ! computationContext
    		
    	case result : Double =>
    		log.info("Result received from server: " + result)
    		mainSenderFuture.completeWithResult(result)
	}	
	
	override def preStart = {
		
		//Register the actor to be invoked remotely
    	Actor.remote.start(Config.clientHost , Config.clientPort)
    	Actor.remote.register(Config.clientId, self)
	}
}

/**
 * Actor that runs on the server.
 * It splits the calculation interval and executes an Actor for each of them.
 * The results of the actors are added and returned to the delegate actor in the client.
 */
class AreaUnderTheCurveDispatcherActor extends Actor {
	var areaSum : Double = 0
	var activeActors : Int = 0
	var initializationFinished : Boolean = false
	var clientActor : ActorRef = null
	
	def receive = { 
    	case computationContext : ComputationContext => 
    		
    		//reset the instance variables
    		areaSum = 0
    		activeActors = 0
    		clientActor = remote.actorFor(Config.clientId, Config.clientHost , Config.clientPort)
    		
    		//Splits the calculation interval in sub-intervals with a maximum length equal to the configured precision.
    		val subintervals : Int = ceil((computationContext.interval.to - computationContext.interval.from) / computationContext.precision).asInstanceOf[Int]
    		val subintervalStep = (computationContext.interval.to - computationContext.interval.from) / subintervals
    		
    		log.info("Will be using " + subintervals + " actors")
    		
    		//Starts a PolynomialIntegralCalculatorActor for each interval
    		var lastX = computationContext.interval.from
    		for(i <- 0 until subintervals) {
    			val actor = actorOf[AreaUnderTheCurveComputerActor].start
    			val toX = lastX + subintervalStep
    			actor ! PolynomialInterval (Interval(lastX, toX), computationContext.coefList)
    			activeActors += 1
    			lastX = toX
    		}
    		initializationFinished = true
    	
    	//Result received for the computation for an interval
    	case result : Double =>
    		areaSum += result
 		
    		//stops the sender actor
    		self.sender.get.stop
    		
    		activeActors -= 1
    		
    		//when all actors have sent their computation, sends the sum to the client
    		if(initializationFinished && activeActors <= 0) {
    			clientActor ! areaSum
    		}
	}
	
	override def preStart = {
		
		//Register the actor to be invoked remotely
    	Actor.remote.start(Config.serverHost , Config.serverPort)
    	Actor.remote.register(Config.serverId, self)
	}
}

/**
 * Calculates an approximation of a polynomial integral using the Simpson formula rule.
 */
class AreaUnderTheCurveComputerActor extends Actor {
	
	def receive = {
    	case polynomialInterval : PolynomialInterval => 
    		self.reply(areaUnderTheCurve(polynomialInterval.coefList, polynomialInterval.interval.from , polynomialInterval.interval.to ))
	}
}

/**
 * Test runner emulating an integral calculation.
 */
object Runner {
	
	/**
	 * Run the client that connects to the remote server to perform the computation.
	 */
	def runClient = {
		
		//Configuration
		// - 1 - 4X + 3X^2 - 2X^3 + X^4 + X^5
		val coefList : List[Double] = List(-1, -4, 3, -2, 1, 4)
		val precision : Double = 0.001
		val interval = Interval(-10, 10)
		val computationContext = ComputationContext(interval, coefList, precision)
		
		//Real integral
		val realStartDate = new Date
		val integralCoefList = integral(coefList)
		val integralValue = (f(interval.to, integralCoefList) -  f(interval.from, integralCoefList))
		val realEndDate = new Date
		
		//Approximation of the integral
		val approxActor = actorOf[AreaUnderTheCurveRemoteDelegateActor].start
		approxActor.setTimeout(60000)
		
		val approxStartDate = new Date
		val future = approxActor !!! computationContext
		future.awaitBlocking
		val approxIntegralValue = future.result.getOrElse(throw new RuntimeException("TIMEOUT")).asInstanceOf[Double]
		val approxEndDate = new Date
		approxActor.stop
		
		//Messages 
		println("Polynomial equation is " + polynomialToString(coefList))
		println("Integral to be calculated in [ " + interval.from + " , " + interval.to + " ]")
		println("Approximate integral calculated using steps of " + precision)
		println("Integral equation is " + polynomialToString(integralCoefList))
		print("Real integral value = " + integralValue)
		println(" (calculated in " + (realEndDate.getTime - realStartDate.getTime) + " ms)")
		print("Approximate integral value = " + approxIntegralValue)
		println(" (calculated in " + (approxEndDate.getTime - approxStartDate.getTime) + " ms)")
		println("Error = " + abs((approxIntegralValue - integralValue ) / integralValue * 100) + "%")
	}
	
	/**
	 * Run the server that listen for client requests.
	 */
	def runServer = {
		 Actor.actorOf[AreaUnderTheCurveDispatcherActor].start
	}
}
