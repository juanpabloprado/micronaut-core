package io.micronaut.kotlin.processing.elementapi

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanMethod
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

class BeanIntrospectionSpec extends Specification {

    void "test basic introspection"() {
        when:
        def introspection = Compiler.buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {

}
""")

        then:
        noExceptionThrown()
        introspection != null
        introspection.instantiate().class.name == "test.Test"
    }

    void "test generics in arrays don't stack overflow"() {
        given:
        def introspection = Compiler.buildBeanIntrospection('arraygenerics.Test', '''
package arraygenerics

import io.micronaut.core.annotation.Introspected
import io.micronaut.context.annotation.Executable 

@Introspected
class Test<T : CharSequence> {

    lateinit var array: Array<T>
    lateinit var starArray: Array<*>
    lateinit var stringArray: Array<String>
    
    @Executable
    fun myMethod(): Array<T> = array
}
''')
        expect:
        introspection.getRequiredProperty("array", CharSequence[].class).type == CharSequence[].class
        introspection.getRequiredProperty("starArray", Object[].class).type == Object[].class
        introspection.getRequiredProperty("stringArray", String[].class).type == String[].class
        introspection.beanMethods.first().returnType.type == CharSequence[].class
    }

    void 'test favor method access'() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD])
class Test {
    var one: String? = null
        private set
        get() {
            invoked = true
            return field
        }
    var invoked = false
}
''')

        when:
        def properties = introspection.getBeanProperties()
        def instance = introspection.instantiate()

        then:
        properties.size() == 2

        when:
        def one = introspection.getRequiredProperty("one", String)
        instance.one = 'test'


        then:
        one.get(instance) == 'test'
        instance.invoked
    }

    void 'test favor field access'() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess;

import io.micronaut.core.annotation.*;


@Introspected(accessKind = [Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD])
class Test {
    var one: String? = null
        private set
        get() {
            invoked = true
            return field
        }
    var invoked = false
}
''');
        when:
        def properties = introspection.getBeanProperties()
        def instance = introspection.instantiate()

        then:
        properties.size() == 2

        when:
        def one = introspection.getRequiredProperty("one", String)
        instance.one = 'test'

        then:
        one.get(instance) == 'test'
        instance.invoked // fields are always private in kotlin so the method will always be referenced
    }

    void 'test field access only'() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.FIELD])
open class Test(val two: Integer?) {  // read-only
    var one: String? = null // read/write
    internal var three: String? = null // package protected
    protected var four: String? = null // not included since protected
    private var five: String? = null // not included since private
}
''');
        when:
        def properties = introspection.getBeanProperties()

        then: 'all fields are private in Kotlin'
        properties.isEmpty()
    }

    void 'test bean constructor'() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('beanctor.Test','''\
package beanctor

import java.net.URL

@io.micronaut.core.annotation.Introspected
class Test @com.fasterxml.jackson.annotation.JsonCreator constructor(private val another: String)
''')


        when:
        def constructor = introspection.getConstructor()
        def newInstance = constructor.instantiate("test")

        then:
        newInstance != null
        newInstance.another == "test"
        !introspection.getAnnotationMetadata().hasDeclaredAnnotation(com.fasterxml.jackson.annotation.JsonCreator)
        constructor.getAnnotationMetadata().hasDeclaredAnnotation(com.fasterxml.jackson.annotation.JsonCreator)
        !constructor.getAnnotationMetadata().hasDeclaredAnnotation(Introspected)
        !constructor.getAnnotationMetadata().hasAnnotation(Introspected)
        !constructor.getAnnotationMetadata().hasStereotype(Introspected)
        constructor.arguments.length == 1
        constructor.arguments[0].type == String
    }

    void "test generate bean method for introspected class"() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.MethodTest', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.context.annotation.Executable

@Introspected
class MethodTest : SuperType(), SomeInt {

    fun nonAnnotated() = true

    @Executable
    override fun invokeMe(str: String): String {
        return str
    }
    
    @Executable
    fun invokePrim(i: Int): Int {
        return i
    }
}

open class SuperType {

    @Executable
    fun superMethod(str: String): String {
        return str
    }
    
    @Executable
    open fun invokeMe(str: String): String {
        return str
    }
}

interface SomeInt {

    @Executable
    fun ok() = true
    
    fun getName() = "ok"
}
''')
        when:
        def properties = introspection.getBeanProperties()
        Collection<BeanMethod> beanMethods = introspection.getBeanMethods()

        then:
        properties.size() == 1
        beanMethods*.name as Set == ['invokeMe', 'invokePrim', 'superMethod', 'ok'] as Set
        beanMethods.every({it.annotationMetadata.hasAnnotation(Executable)})
        beanMethods.every { it.declaringBean == introspection}

        when:

        def invokeMe = beanMethods.find { it.name == 'invokeMe' }
        def invokePrim = beanMethods.find { it.name == 'invokePrim' }
        def itfeMethod = beanMethods.find { it.name == 'ok' }
        def bean = introspection.instantiate()

        then:
        invokeMe instanceof ExecutableMethod
        invokeMe.invoke(bean, "test") == 'test'
        invokePrim.invoke(bean, 10) == 10
        itfeMethod.invoke(bean) == true
    }

    void "test custom with prefix"() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('customwith.CopyMe', '''\
package customwith

import java.net.URL
import java.util.Locale

@io.micronaut.core.annotation.Introspected(withPrefix = "alter")
class CopyMe(val another: String) {
    
    fun alterAnother(another: String): CopyMe {
        return if (another == this.another) {
            this
        } else {
            CopyMe(another.uppercase(Locale.getDefault()))   
        }
    }
}
''')
        when:
        def another = introspection.getRequiredProperty("another", String)
        def newInstance = introspection.instantiate("test")

        then:
        newInstance.another == "test"

        when:"An explicit with method is used"
        def result = another.withValue(newInstance, "changed")

        then:"It was invoked"
        !result.is(newInstance)
        result.another == 'CHANGED'
    }

    void "test copy constructor via mutate method"() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.CopyMe','''\
package test

import java.net.URL
import java.util.Locale

@io.micronaut.core.annotation.Introspected
class CopyMe(val name: String,
             val another: String) {

    var url: URL? = null

    fun withAnother(a: String): CopyMe {
        return if (this.another == a) {
            this
        } else {
            CopyMe(this.name, a.uppercase(Locale.getDefault()))
        }
    }
}
''')
        when:
        def copyMe = introspection.instantiate("Test", "Another")
        def expectUrl = new URL("http://test.com")
        copyMe.url = expectUrl

        then:
        copyMe.name == 'Test'
        copyMe.another == "Another"
        copyMe.url == expectUrl


        when:
        def property = introspection.getRequiredProperty("name", String)
        def another = introspection.getRequiredProperty("another", String)
        def newInstance = property.withValue(copyMe, "Changed")

        then:
        !newInstance.is(copyMe)
        newInstance.name == 'Changed'
        newInstance.url == expectUrl
        newInstance.another == "Another"

        when:"the instance is changed with the same value"
        def result = property.withValue(newInstance, "Changed")

        then:"The existing instance is returned"
        newInstance.is(result)

        when:"An explicit with method is used"
        result = another.withValue(newInstance, "changed")

        then:"It was invoked"
        !result.is(newInstance)
        result.another == 'CHANGED'
    }

    void "test secondary constructor for data classes"() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
package test

@io.micronaut.core.annotation.Introspected
data class Foo(val x: Int, val y: Int) {
    
    constructor(x: Int) : this(x, 20)
    
    constructor() : this(20, 20)
}
''')
        when:
        def obj = introspection.instantiate(5, 10)

        then:
        obj.getX() == 5
        obj.getY() == 10
    }

    void "test secondary constructor with @Creator for data classes"() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
package test

import io.micronaut.core.annotation.Creator

@io.micronaut.core.annotation.Introspected
data class Foo(val x: Int, val y: Int) {

    @Creator
    constructor(x: Int) : this(x, 20)
    
    constructor() : this(20, 20)
}
''')
        when:
        def obj = introspection.instantiate(5)

        then:
        obj.getX() == 5
        obj.getY() == 20
    }
}
