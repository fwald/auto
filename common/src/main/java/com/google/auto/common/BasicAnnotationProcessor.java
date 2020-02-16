/*
 * Copyright 2014 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.common;

import static com.google.auto.common.MoreElements.isAnnotationPresent;
import static com.google.auto.common.SuperficialValidation.validateElement;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Multimaps.filterKeys;
import static javax.lang.model.element.ElementKind.PACKAGE;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.base.Ascii;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ErrorType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor8;

/**
 * An abstract {@link Processor} implementation that defers processing of {@link Element}s to later
 * rounds if they cannot be processed.
 *
 * <p>Subclasses put their processing logic in {@link ProcessingStep} implementations. The
 * steps are passed to the processor by returning them in the {@link #initSteps()} method, and can
 * access the {@link ProcessingEnvironment} using {@link #processingEnv}.
 *
 * Any logic that needs to happen once per round can be specified by overriding
 * {@link #postRound(RoundEnvironment)}.
 *
 * <h3>Ill-formed elements are deferred</h3>
 * Any annotated element whose nearest enclosing type is not well-formed is deferred, and not passed
 * to any {@code ProcessingStep}. This helps processors to avoid many common pitfalls, such as
 * {@link ErrorType} instances, {@link ClassCastException}s and badly coerced types.
 *
 * <p>A non-package element is considered well-formed if its type, type parameters, parameters,
 * default values, supertypes, annotations, and enclosed elements are. Package elements are treated
 * similarly, except that their enclosed elements are not validated. See
 * {@link SuperficialValidation#validateElement(Element)} for details.
 *
 * <p>The primary disadvantage to this validation is that any element that forms a circular
 * dependency with a type generated by another {@code BasicAnnotationProcessor} will never compile
 * because the element will never be fully complete. All such compilations will fail with an error
 * message on the offending type that describes the issue.
 *
 * <h3>Each {@code ProcessingStep} can defer elements</h3>
 *
 * <p>Each {@code ProcessingStep} can defer elements by including them in the set returned by
 * {@link ProcessingStep#process(SetMultimap)}; elements deferred by a step will be passed back to
 * that step in a later round of processing.
 *
 * <p>This feature is useful when one processor may depend on code generated by another,
 * independent processor, in a way that isn't caught by the well-formedness check described above.
 * For example, if an element {@code A} cannot be processed because processing it depends on the
 * existence of some class {@code B}, then {@code A} should be deferred until a later round of
 * processing, when {@code B} will have been generated by another processor.
 *
 * <p>If {@code A} directly references {@code B}, then the well-formedness check will correctly
 * defer processing of {@code A} until {@code B} has been generated.
 *
 * <p>However, if {@code A} references {@code B} only indirectly (for example, from within a method
 * body), then the well-formedness check will not defer processing {@code A}, but a processing step
 * can reject {@code A}.
 */
public abstract class BasicAnnotationProcessor extends AbstractProcessor {

  private final Set<ElementName> deferredElementNames = new LinkedHashSet<ElementName>();
  private final SetMultimap<ProcessingStep, ElementName> elementsDeferredBySteps =
      LinkedHashMultimap.create();

  private Elements elements;
  private Messager messager;
  private ImmutableList<? extends ProcessingStep> steps;

  @Override
  public final synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.elements = processingEnv.getElementUtils();
    this.messager = processingEnv.getMessager();
    this.steps = ImmutableList.copyOf(initSteps());
  }

  /**
   * Creates {@linkplain ProcessingStep processing steps} for this processor.
   * {@link #processingEnv} is guaranteed to be set when this method is invoked.
   */
  protected abstract Iterable<? extends ProcessingStep> initSteps();

  /**
   * An optional hook for logic to be executed at the end of each round.
   *
   * @deprecated use {@link #postRound(RoundEnvironment)} instead
   */
  @Deprecated
  protected void postProcess() {}

  /** An optional hook for logic to be executed at the end of each round. */
  protected void postRound(RoundEnvironment roundEnv) {
    if (!roundEnv.processingOver()) {
      postProcess();
    }
  }

  private ImmutableSet<? extends Class<? extends Annotation>> getSupportedAnnotationClasses() {
    checkState(steps != null);
    ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.builder();
    for (ProcessingStep step : steps) {
      builder.addAll(step.annotations());
    }
    return builder.build();
  }

  /**
   * Returns the set of supported annotation types as a  collected from registered
   * {@linkplain ProcessingStep processing steps}.
   */
  @Override
  public final ImmutableSet<String> getSupportedAnnotationTypes() {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    for (Class<? extends Annotation> annotationClass : getSupportedAnnotationClasses()) {
      builder.add(annotationClass.getCanonicalName());
    }
    return builder.build();
  }

  @Override
  public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    checkState(elements != null);
    checkState(messager != null);
    checkState(steps != null);

    ImmutableMap<String, Optional<? extends Element>> deferredElements = deferredElements();

    deferredElementNames.clear();

    // If this is the last round, report all of the missing elements if there
    // were no errors raised in the round; otherwise reporting the missing
    // elements just adds noise the output.
    if (roundEnv.processingOver()) {
      postRound(roundEnv);
      if (!roundEnv.errorRaised()) {
        reportMissingElements(deferredElements, elementsDeferredBySteps.values());
      }
      return false;
    }

    process(validElements(deferredElements, roundEnv));

    postRound(roundEnv);

    return false;
  }

  /** Processes the valid elements, including those previously deferred by each step. */
  private void process(ImmutableSetMultimap<Class<? extends Annotation>, Element> validElements) {
    for (ProcessingStep step : steps) {
      ImmutableSetMultimap<Class<? extends Annotation>, Element> stepElements =
          new ImmutableSetMultimap.Builder<Class<? extends Annotation>, Element>()
              .putAll(indexByAnnotation(elementsDeferredBySteps.get(step), step.annotations()))
              .putAll(filterKeys(validElements, Predicates.<Object>in(step.annotations())))
              .build();
      if (stepElements.isEmpty()) {
        elementsDeferredBySteps.removeAll(step);
      } else {
        Set<? extends Element> rejectedElements = step.process(stepElements);
        elementsDeferredBySteps.replaceValues(
            step,
            transform(
                rejectedElements,
                new Function<Element, ElementName>() {
                  @Override
                  public ElementName apply(Element element) {
                    return ElementName.forAnnotatedElement(element);
                  }
                }));
      }
    }
  }

  /** Returns the previously deferred elements. */
  private ImmutableMap<String, Optional<? extends Element>> deferredElements() {
    ImmutableMap.Builder<String, Optional<? extends Element>> deferredElements =
        ImmutableMap.builder();
    for (ElementName elementName : deferredElementNames) {
      deferredElements.put(elementName.name(), elementName.getElement(elements));
    }
    return deferredElements.build();
  }

  private void reportMissingElements(
      Map<String, ? extends Optional<? extends Element>> missingElements,
      Collection<ElementName> missingElementNames) {
    if (!missingElementNames.isEmpty()) {
      // Branch 0
      iZafiroInstrument.reportMissingElementsBranches[0] = 1;
      ImmutableMap.Builder<String, Optional<? extends Element>> allMissingElements =
          ImmutableMap.builder();
      allMissingElements.putAll(missingElements);
      for (ElementName missingElement : missingElementNames) {
        // Branch 2
        iZafiroInstrument.reportMissingElementsBranches[2] = 1;
        if (!missingElements.containsKey(missingElement.name())) {
          // Branch 4
          iZafiroInstrument.reportMissingElementsBranches[4] = 1;
          allMissingElements.put(missingElement.name(), missingElement.getElement(elements));
        }
        else {
          // Branch 5
          iZafiroInstrument.reportMissingElementsBranches[5] = 1;
        }
      }
      // Branch 3
      iZafiroInstrument.reportMissingElementsBranches[3] = 1;
      missingElements = allMissingElements.build();
    }
    else {
      // Branch 1
      iZafiroInstrument.reportMissingElementsBranches[1] = 1;
    }
    for (Entry<String, ? extends Optional<? extends Element>> missingElementEntry :
        missingElements.entrySet()) {
      // Branch 6
      iZafiroInstrument.reportMissingElementsBranches[6] = 1;
      Optional<? extends Element> missingElement = missingElementEntry.getValue();
      if (missingElement.isPresent()) {
        // Branch 8
        iZafiroInstrument.reportMissingElementsBranches[8] = 1;
        processingEnv
            .getMessager()
            .printMessage(
                ERROR,
                processingErrorMessage(
                    "this " + Ascii.toLowerCase(missingElement.get().getKind().name())),
                missingElement.get());
      } else {
        // Branch 9
        iZafiroInstrument.reportMissingElementsBranches[9] = 1;
        processingEnv
            .getMessager()
            .printMessage(ERROR, processingErrorMessage(missingElementEntry.getKey()));
      }
    }
    // Branch 7
    iZafiroInstrument.reportMissingElementsBranches[7] = 1;
  }

  private String processingErrorMessage(String target) {
    return String.format(
        "[%s:MiscError] %s was unable to process %s because not all of its dependencies could be "
            + "resolved. Check for compilation errors or a circular dependency with generated "
            + "code.",
        getClass().getSimpleName(),
        getClass().getCanonicalName(),
        target);
  }

  /**
   * Returns the valid annotated elements contained in all of the deferred elements. If none are
   * found for a deferred element, defers it again.
   */
  private ImmutableSetMultimap<Class<? extends Annotation>, Element> validElements(
      ImmutableMap<String, Optional<? extends Element>> deferredElements,
      RoundEnvironment roundEnv) {
    ImmutableSetMultimap.Builder<Class<? extends Annotation>, Element>
        deferredElementsByAnnotationBuilder = ImmutableSetMultimap.builder();
    for (Entry<String, Optional<? extends Element>> deferredTypeElementEntry :
        deferredElements.entrySet()) {
      Optional<? extends Element> deferredElement = deferredTypeElementEntry.getValue();
      if (deferredElement.isPresent()) {
        findAnnotatedElements(
            deferredElement.get(),
            getSupportedAnnotationClasses(),
            deferredElementsByAnnotationBuilder);
      } else {
        deferredElementNames.add(ElementName.forTypeName(deferredTypeElementEntry.getKey()));
      }
    }

    ImmutableSetMultimap<Class<? extends Annotation>, Element> deferredElementsByAnnotation =
        deferredElementsByAnnotationBuilder.build();

    ImmutableSetMultimap.Builder<Class<? extends Annotation>, Element> validElements =
        ImmutableSetMultimap.builder();

    Set<ElementName> validElementNames = new LinkedHashSet<ElementName>();

    // Look at the elements we've found and the new elements from this round and validate them.
    for (Class<? extends Annotation> annotationClass : getSupportedAnnotationClasses()) {
      // This should just call roundEnv.getElementsAnnotatedWith(Class) directly, but there is a bug
      // in some versions of eclipse that cause that method to crash.
      TypeElement annotationType = elements.getTypeElement(annotationClass.getCanonicalName());
      Set<? extends Element> elementsAnnotatedWith =
          (annotationType == null)
              ? ImmutableSet.<Element>of()
              : roundEnv.getElementsAnnotatedWith(annotationType);
      for (Element annotatedElement :
          Sets.union(elementsAnnotatedWith, deferredElementsByAnnotation.get(annotationClass))) {
        if (annotatedElement.getKind().equals(PACKAGE)) {
          PackageElement annotatedPackageElement = (PackageElement) annotatedElement;
          ElementName annotatedPackageName =
              ElementName.forPackageName(annotatedPackageElement.getQualifiedName().toString());
          boolean validPackage =
              validElementNames.contains(annotatedPackageName)
                  || (!deferredElementNames.contains(annotatedPackageName)
                      && validateElement(annotatedPackageElement));
          if (validPackage) {
            validElements.put(annotationClass, annotatedPackageElement);
            validElementNames.add(annotatedPackageName);
          } else {
            deferredElementNames.add(annotatedPackageName);
          }
        } else {
          TypeElement enclosingType = getEnclosingType(annotatedElement);
          ElementName enclosingTypeName =
              ElementName.forTypeName(enclosingType.getQualifiedName().toString());
          boolean validEnclosingType =
              validElementNames.contains(enclosingTypeName)
                  || (!deferredElementNames.contains(enclosingTypeName)
                      && validateElement(enclosingType));
          if (validEnclosingType) {
            validElements.put(annotationClass, annotatedElement);
            validElementNames.add(enclosingTypeName);
          } else {
            deferredElementNames.add(enclosingTypeName);
          }
        }
      }
    }

    return validElements.build();
  }

  private ImmutableSetMultimap<Class<? extends Annotation>, Element> indexByAnnotation(
      Set<ElementName> annotatedElements,
      Set<? extends Class<? extends Annotation>> annotationClasses) {
    ImmutableSetMultimap.Builder<Class<? extends Annotation>, Element> deferredElements =
        ImmutableSetMultimap.builder();
    for (ElementName elementName : annotatedElements) {
      Optional<? extends Element> element = elementName.getElement(elements);
      if (element.isPresent()) {
        findAnnotatedElements(element.get(), annotationClasses, deferredElements);
      }
    }
    return deferredElements.build();
  }

  /**
   * Adds {@code element} and its enclosed elements to {@code annotatedElements} if they are
   * annotated with any annotations in {@code annotationClasses}. Does not traverse to member types
   * of {@code element}, so that if {@code Outer} is passed in the example below, looking for
   * {@code @X}, then {@code Outer}, {@code Outer.foo}, and {@code Outer.foo()} will be added to the
   * multimap, but neither {@code Inner} nor its members will.
   *
   * <pre><code>
   *   {@literal @}X class Outer {
   *     {@literal @}X Object foo;
   *     {@literal @}X void foo() {}
   *     {@literal @}X static class Inner {
   *       {@literal @}X Object bar;
   *       {@literal @}X void bar() {}
   *     }
   *   }
   * </code></pre>
   */
  private static void findAnnotatedElements(
      Element element,
      Set<? extends Class<? extends Annotation>> annotationClasses,
      ImmutableSetMultimap.Builder<Class<? extends Annotation>, Element> annotatedElements) {
    for (Element enclosedElement : element.getEnclosedElements()) {
      if (!enclosedElement.getKind().isClass() && !enclosedElement.getKind().isInterface()) {
        findAnnotatedElements(enclosedElement, annotationClasses, annotatedElements);
      }
    }

    // element.getEnclosedElements() does NOT return parameter elements
    if (element instanceof ExecutableElement) {
      for (Element parameterElement : ((ExecutableElement) element).getParameters()) {
        findAnnotatedElements(parameterElement, annotationClasses, annotatedElements);
      }
    }
    for (Class<? extends Annotation> annotationClass : annotationClasses) {
      if (isAnnotationPresent(element, annotationClass)) {
        annotatedElements.put(annotationClass, element);
      }
    }
  }

  /**
   * Returns the nearest enclosing {@link TypeElement} to the current element, throwing
   * an {@link IllegalArgumentException} if the provided {@link Element} is a
   * {@link PackageElement} or is otherwise not enclosed by a type.
   */
  // TODO(cgruber) move to MoreElements and make public.
  private static TypeElement getEnclosingType(Element element) {
    return element.accept(new SimpleElementVisitor8<TypeElement, Void>() {
      @Override protected TypeElement defaultAction(Element e, Void p) {
        return e.getEnclosingElement().accept(this, p);
      }

      @Override public TypeElement visitType(TypeElement e, Void p) {
        return e;
      }

      @Override public TypeElement visitPackage(PackageElement e, Void p) {
        throw new IllegalArgumentException();
      }
    }, null);
  }

  /**
   * The unit of processing logic that runs under the guarantee that all elements are complete and
   * well-formed. A step may reject elements that are not ready for processing but may be at a later
   * round.
   */
  public interface ProcessingStep {
    /** The set of annotation types processed by this step. */
    Set<? extends Class<? extends Annotation>> annotations();

    /**
     * The implementation of processing logic for the step. It is guaranteed that the keys in {@code
     * elementsByAnnotation} will be a subset of the set returned by {@link #annotations()}.
     *
     * @return the elements (a subset of the values of {@code elementsByAnnotation}) that this step
     *     is unable to process, possibly until a later processing round. These elements will be
     *     passed back to this step at the next round of processing.
     */
    Set<? extends Element> process(
        SetMultimap<Class<? extends Annotation>, Element> elementsByAnnotation);
  }

  /**
   * A package or type name.
   *
   * <p>It's unfortunate that we have to track types and packages separately, but since there are
   * two different methods to look them up in {@link Elements}, we end up with a lot of parallel
   * logic. :(
   *
   * <p>Packages declared (and annotated) in {@code package-info.java} are tracked as deferred
   * packages, type elements are tracked directly, and all other elements are tracked via their
   * nearest enclosing type.
   */
  private static final class ElementName {
    private enum Kind {
      PACKAGE_NAME,
      TYPE_NAME,
    }

    private final Kind kind;
    private final String name;

    private ElementName(Kind kind, String name) {
      this.kind = checkNotNull(kind);
      this.name = checkNotNull(name);
    }

    /**
     * An {@link ElementName} for a package.
     */
    static ElementName forPackageName(String packageName) {
      return new ElementName(Kind.PACKAGE_NAME, packageName);
    }

    /**
     * An {@link ElementName} for a type.
     */
    static ElementName forTypeName(String typeName) {
      return new ElementName(Kind.TYPE_NAME, typeName);
    }

    /**
     * An {@link ElementName} for an annotated element. If {@code element} is a package, uses the
     * fully qualified name of the package. If it's a type, uses its fully qualified name.
     * Otherwise, uses the fully-qualified name of the nearest enclosing type.
     */
    static ElementName forAnnotatedElement(Element element) {
      return element.getKind() == PACKAGE
          ? ElementName.forPackageName(((PackageElement) element).getQualifiedName().toString())
          : ElementName.forTypeName(getEnclosingType(element).getQualifiedName().toString());
    }

    /**
     * The fully-qualified name of the element.
     */
    String name() {
      return name;
    }

    /**
     * The {@link Element} whose fully-qualified name is {@link #name()}. Absent if the relevant
     * method on {@link Elements} returns {@code null}.
     */
    Optional<? extends Element> getElement(Elements elements) {
      return Optional.fromNullable(
          kind == Kind.PACKAGE_NAME
              ? elements.getPackageElement(name)
              : elements.getTypeElement(name));
    }

    @Override
    public boolean equals(Object object) {
      if (!(object instanceof ElementName)) {
        return false;
      }

      ElementName that = (ElementName) object;
      return this.kind == that.kind && this.name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(kind, name);
    }
  }
}
