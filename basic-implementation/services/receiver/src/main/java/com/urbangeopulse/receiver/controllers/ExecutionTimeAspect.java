package com.urbangeopulse.receiver.controllers;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

@Aspect
@Component
public class ExecutionTimeAspect {

    private final static Logger logger = Logger.getLogger(ExecutionTimeAspect.class.getName());
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("execution(* com.urbangeopulse.*.controllers.*.*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        logger.info(String.format("Staring to execute .. %s\n\twith parameters: %s.", joinPoint.getSignature(), Arrays.toString(joinPoint.getArgs())));
        Instant startTime = Instant.now();
        Object result = null;
        try {
            result = joinPoint.proceed();
            final long elapsedTimeInMS = Duration.between(startTime, Instant.now()).toMillis();
            logger.info(String.format("Completed in %,d ms (== %.1f minutes) : %s\n\twith parameters: %s.",
                    elapsedTimeInMS, (double)elapsedTimeInMS / 60000, joinPoint.getSignature(), Arrays.toString(joinPoint.getArgs())));
        }
        catch (Exception ex){
            com.urbangeopulse.utils.misc.Logger.logException(ex, logger, Level.SEVERE);
        }

        return result;
    }
}