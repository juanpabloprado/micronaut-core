/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.visitor;

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.isJavaPackagePrivate
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.ElementMutableAnnotationMetadataDelegate
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate

abstract class AbstractKotlinElement<T : KSNode>(protected val declaration: T,
                                     protected  val annotationMetadataFactory: ElementAnnotationMetadataFactory,
                                     protected val visitorContext: KotlinVisitorContext) : Element, ElementMutableAnnotationMetadataDelegate<Element> {

    protected var presetAnnotationMetadata: AnnotationMetadata? = null
    private var elementAnnotationMetadata: ElementAnnotationMetadata? = null

    override fun getNativeType(): T {
        return declaration
    }

    override fun isProtected(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.getVisibility() == Visibility.PROTECTED
        } else {
            false
        }
    }

    protected fun makeCopy(): AbstractKotlinElement<T> {
        val element: AbstractKotlinElement<T> = copyThis()
        copyValues(element)
        return element
    }

    /**
     * @return copy of this element
     */
    protected abstract fun copyThis(): AbstractKotlinElement<T>

    /**
     * @param element the values to be copied to
     */
    protected open fun copyValues(element: AbstractKotlinElement<T>) {
        element.presetAnnotationMetadata = presetAnnotationMetadata
    }
    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): Element? {
        val kotlinElement: AbstractKotlinElement<T> = makeCopy()
        kotlinElement.presetAnnotationMetadata = annotationMetadata
        return kotlinElement
    }

    override fun getAnnotationMetadata(): MutableAnnotationMetadataDelegate<*> {
        if (elementAnnotationMetadata == null) {
            if (presetAnnotationMetadata == null) {
                elementAnnotationMetadata = annotationMetadataFactory.build(this)
            } else {
                elementAnnotationMetadata = annotationMetadataFactory.build(this, presetAnnotationMetadata)
            }
        }
        return elementAnnotationMetadata!!
    }

    override fun isPublic(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.getVisibility() == Visibility.PUBLIC
        } else {
            false
        }
    }

    override fun isPrivate(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.getVisibility() == Visibility.PRIVATE
        } else {
            false
        }
    }

    override fun isFinal(): Boolean {
        return if (declaration is KSDeclaration) {
            !declaration.isOpen() || declaration.modifiers.contains(Modifier.FINAL)
        } else {
            false
        }
    }

    override fun isAbstract(): Boolean {
        return if (declaration is KSModifierListOwner) {
            declaration.modifiers.contains(Modifier.ABSTRACT)
        } else {
            false
        }
    }

    override fun <T : Annotation?> annotate(
        annotationType: String?,
        consumer: Consumer<AnnotationValueBuilder<T>>?
    ): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType, consumer)
    }

    override fun annotate(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType)
    }

    override fun <T : Annotation?> annotate(
        annotationType: Class<T>?,
        consumer: Consumer<AnnotationValueBuilder<T>>?
    ): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType, consumer)
    }

    override fun <T : Annotation?> annotate(annotationType: Class<T>?): Element? {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationType)
    }
    override fun <T : Annotation?> annotate(annotationValue: AnnotationValue<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.annotate(annotationValue)
    }

    override fun removeAnnotation(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotation(annotationType)
    }

    override fun <T : Annotation?> removeAnnotation(annotationType: Class<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotation(annotationType)
    }

    override fun <T : Annotation?> removeAnnotationIf(predicate: Predicate<AnnotationValue<T>>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeAnnotationIf(predicate)
    }

    override fun removeStereotype(annotationType: String?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeStereotype(annotationType)
    }

    override fun <T : Annotation?> removeStereotype(annotationType: Class<T>?): Element {
        return super<ElementMutableAnnotationMetadataDelegate>.removeStereotype(annotationType)
    }

    override fun isPackagePrivate(): Boolean {
        return if (declaration is KSDeclaration) {
            declaration.isJavaPackagePrivate()
        } else {
            false
        }
    }

    override fun getDocumentation(): Optional<String> {
        return if (declaration is KSDeclaration) {
            Optional.ofNullable(declaration.docString)
        } else {
            Optional.empty()
        }
    }

    override fun getReturnInstance(): Element {
        return this
    }
}
