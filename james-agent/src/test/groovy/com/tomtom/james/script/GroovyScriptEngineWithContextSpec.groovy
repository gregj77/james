/*
 * Copyright 2017 TomTom International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tomtom.james.script

import com.tomtom.james.agent.ToolkitManager
import com.tomtom.james.common.api.informationpoint.InformationPoint
import com.tomtom.james.common.api.informationpoint.Metadata
import com.tomtom.james.common.api.publisher.Event
import com.tomtom.james.common.api.publisher.EventPublisher
import com.tomtom.james.common.api.script.RuntimeInformationPointParameter
import com.tomtom.james.common.log.Logger
import spock.lang.Specification

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture

class GroovyScriptEngineWithContextSpec extends Specification {

    def script = '''
import com.tomtom.james.common.api.publisher.Event
import com.tomtom.james.script.ErrorHandlerContext
import com.tomtom.james.script.SuccessHandlerContext
import com.tomtom.james.script.PrepareContextHandlerContext

def onPrepareContext(PrepareContextHandlerContext context) {
    return "Key:" + context.key
}

def onSuccess(SuccessHandlerContext context) {
    def eventMap = [
            result     : "success",
            className  : context.informationPointClassName,
            methodName : context.informationPointMethodName,
            initialContext: context.initialContext,
    ]
    context.parameters.each {
        eventMap["arg(${it.name})"] = it.value
    }
    publishEvent(new Event(eventMap))
}

def onError(ErrorHandlerContext context) {
    def eventMap = [
            result     : "error",
            className  : context.informationPointClassName,
            methodName : context.informationPointMethodName,
            initialContext: context.initialContext,
    ]
    context.parameters.each {
        eventMap["arg(${it.name})"] = it.value
    }
    publishEvent(new Event(eventMap))
}
'''

    def eventPublisher = Mock(EventPublisher)
    def toolkitManager = Mock(ToolkitManager)

    def informationPointClassName = "informationPointClassName"
    def informationPointMethodName = "informationPointClassName"
    def duration = Duration.of(1, ChronoUnit.SECONDS)
    def instance = Mock(Object)
    def param1 = Mock(RuntimeInformationPointParameter)
    def param2 = Mock(RuntimeInformationPointParameter)
    def returnValue = Mock(Object)
    def errorCause = Mock(Throwable)
    def callStack = null
    def origin = null
    def currentThread = Mock(Thread)
    def informationPoint = Mock(InformationPoint)

    def setup() {
        Logger.setCurrentLogLevel(Logger.Level.TRACE)

        param1.getName() >> "param1-name"
        param1.getValue() >> "param1-value"
        param2.getName() >> "param2-name"
        param2.getValue() >> "param2-value"

        informationPoint.getClassName() >> informationPointClassName
        informationPoint.getMethodName() >> informationPointMethodName
        informationPoint.getScript() >> Optional.of(script)
        informationPoint.getMetadata() >> new Metadata()
    }

    def "Should publish an event when success handler invoked"() {
        given:
        def engine = new GroovyScriptEngine(eventPublisher, toolkitManager)

        when:
        def context = engine.invokePrepareContext(informationPoint, origin, [param1, param2], instance, currentThread, "my_key")
        engine.invokeSuccessHandler(informationPoint, origin, [param1, param2],
                instance, currentThread, duration, callStack, returnValue, CompletableFuture.completedFuture(context))

        then:
        1 * eventPublisher.publish({ Event evt ->
            evt.content == [
                    result            : "success",
                    className         : informationPointClassName,
                    methodName        : informationPointMethodName,
                    initialContext    : "Key:my_key",
                    "arg(param1-name)": "param1-value",
                    "arg(param2-name)": "param2-value"
            ]
        })
    }

    def "Should publish an event when error handler invoked"() {
        given:
        def engine = new GroovyScriptEngine(eventPublisher, toolkitManager)

        when:
        def context = engine.invokePrepareContext(informationPoint, origin, [param1, param2], instance, currentThread, "my_key")
        engine.invokeErrorHandler(informationPoint, origin, [param1, param2],
                instance, currentThread, duration, callStack, errorCause, CompletableFuture.completedFuture(context))

        then:
        1 * eventPublisher.publish({ Event evt ->
            evt.content == [
                    result            : "error",
                    className         : informationPointClassName,
                    methodName        : informationPointMethodName,
                    initialContext    : "Key:my_key",
                    "arg(param1-name)": "param1-value",
                    "arg(param2-name)": "param2-value"
            ]
        })
    }
}
