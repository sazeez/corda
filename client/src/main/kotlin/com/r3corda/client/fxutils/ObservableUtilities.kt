package com.r3corda.client.fxutils

import javafx.beans.binding.Bindings
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.MapChangeListener
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import javafx.collections.transformation.FilteredList
import org.fxmisc.easybind.EasyBind
import org.slf4j.LoggerFactory
import java.util.function.Predicate
import kotlin.concurrent.thread

/**
 * Here follows utility extension functions that help reduce the visual load when developing RX code. Each function should
 * have a short accompanying example code.
 */

/**
 * val person: ObservableValue<Person> = (..)
 * val personName: ObservableValue<String> = person.map { it.name }
 */
fun <A, B> ObservableValue<out A>.map(function: (A) -> B): ObservableValue<B> = EasyBind.map(this, function)

/**
 * val dogs: ObservableList<Dog> = (..)
 * val dogOwners: ObservableList<Person> = dogs.map { it.owner }
 *
 * @param cached If true the results of the mapped function are cached in a backing list. If false each get() will
 *     re-run the function.
 */
fun <A, B> ObservableList<out A>.map(cached: Boolean = true, function: (A) -> B): ObservableList<B> {
    if (cached) {
        return MappedList(this, function)
    } else {
        return EasyBind.map(this, function)
    }
}

/**
 * val aliceHeight: ObservableValue<Long> = (..)
 * val bobHeight: ObservableValue<Long> = (..)
 * fun sumHeight(a: Long, b: Long): Long { .. }
 *
 * val aliceBobSumHeight = ::sumHeight.lift(aliceHeight, bobHeight)
 * val aliceHeightPlus2 = ::sumHeight.lift(aliceHeight, 2L.lift())
 */
fun <A> A.lift(): ObservableValue<A> = ReadOnlyObjectWrapper(this)
fun <A, R> ((A) -> R).lift(
        arg0: ObservableValue<A>
): ObservableValue<R> = EasyBind.map(arg0, this)
fun <A, B, R> ((A, B) -> R).lift(
        arg0: ObservableValue<A>,
        arg1: ObservableValue<B>
): ObservableValue<R> = EasyBind.combine(arg0, arg1, this)
fun <A, B, C, R> ((A, B, C) -> R).lift(
        arg0: ObservableValue<A>,
        arg1: ObservableValue<B>,
        arg2: ObservableValue<C>
): ObservableValue<R> = EasyBind.combine(arg0, arg1, arg2, this)
fun <A, B, C, D, R> ((A, B, C, D) -> R).lift(
        arg0: ObservableValue<A>,
        arg1: ObservableValue<B>,
        arg2: ObservableValue<C>,
        arg3: ObservableValue<D>
): ObservableValue<R> = EasyBind.combine(arg0, arg1, arg2, arg3, this)

/**
 * data class Person(val height: ObservableValue<Long>)
 * val person: ObservableValue<Person> = (..)
 * val personHeight: ObservableValue<Long> = person.bind { it.height }
 */
fun <A, B> ObservableValue<out A>.bind(function: (A) -> ObservableValue<B>): ObservableValue<B> =
        EasyBind.monadic(this).flatMap(function)
/**
 * A variant of [bind] that has out variance on the output type. This is sometimes useful when kotlin is too eager to
 * propagate variance constraints and type inference fails.
 */
fun <A, B> ObservableValue<out A>.bindOut(function: (A) -> ObservableValue<out B>): ObservableValue<out B> =
        @Suppress("UNCHECKED_CAST")
        EasyBind.monadic(this).flatMap(function as (A) -> ObservableValue<B>)

/**
 * enum class FilterCriterion { HEIGHT, NAME }
 * val filterCriterion: ObservableValue<FilterCriterion> = (..)
 * val people: ObservableList<Person> = (..)
 * fun filterFunction(filterCriterion: FilterCriterion): (Person) -> Boolean { .. }
 *
 * val filteredPeople: ObservableList<Person> = people.filter(filterCriterion.map(filterFunction))
 */
fun <A> ObservableList<out A>.filter(predicate: ObservableValue<(A) -> Boolean>): ObservableList<A> {
    // We cast here to enforce variance, FilteredList should be covariant
    @Suppress("UNCHECKED_CAST")
    return FilteredList<A>(this as ObservableList<A>).apply {
        predicateProperty().bind(predicate.map { predicateFunction ->
            Predicate<A> { predicateFunction(it) }
        })
    }
}

/**
 * data class Dog(val owner: Person?)
 * val dogs: ObservableList<Dog> = (..)
 * val owners: ObservableList<Person> = dogs.map(Dog::owner).filterNotNull()
 */
fun <A> ObservableList<out A?>.filterNotNull(): ObservableList<A> {
    @Suppress("UNCHECKED_CAST")
    return filtered { it != null } as ObservableList<A>
}

/**
 * val people: ObservableList<Person> = (..)
 * val concatenatedNames = people.foldObservable("", { names, person -> names + person.name })
 * val concatenatedNames2 = people.map(Person::name).fold("", String::plus)
 */
fun <A, B> ObservableList<out A>.foldObservable(initial: B, folderFunction: (B, A) -> B): ObservableValue<B> {
    return Bindings.createObjectBinding({
        var current = initial
        forEach {
            current = folderFunction(current, it)
        }
        current
    }, arrayOf(this))
}

/**
 * data class Person(val height: ObservableValue<Long>)
 * val people: ObservableList<Person> = (..)
 * val heights: ObservableList<Long> = people.map(Person::height).flatten()
 */
fun <A> ObservableList<out ObservableValue<out A>>.flatten(): ObservableList<A> = FlattenedList(this)

/**
 * data class Person(val height: ObservableValue<Long>)
 * val people: List<Person> = listOf(alice, bob)
 * val heights: ObservableList<Long> = people.map(Person::height).sequence()
 */
fun <A> Collection<ObservableValue<out A>>.sequence(): ObservableList<A> = FlattenedList(FXCollections.observableArrayList(this))

/**
 * data class Person(val height: Long)
 * val people: ObservableList<Person> = (..)
 * val nameToHeight: ObservableMap<String, Long> = people.associateBy(Person::name) { name, person -> person.height }
 */
fun <K, A, B> ObservableList<out A>.associateBy(toKey: (A) -> K, assemble: (K, A) -> B): ObservableMap<K, B> {
    return AssociatedList(this, toKey, assemble)
}

/**
 * val people: ObservableList<Person> = (..)
 * val nameToPerson: ObservableMap<String, Person> = people.associateBy(Person::name)
 */
fun <K, A> ObservableList<out A>.associateBy(toKey: (A) -> K): ObservableMap<K, A> {
    return associateBy(toKey) { key, value -> value }
}

/**
 * val people: ObservableList<Person> = (..)
 * val heightToNames: ObservableMap<Long, ObservableList<String>> = people.associateByAggregation(Person::height) { name, person -> person.name }
 */
fun <K : Any, A : Any, B> ObservableList<out A>.associateByAggregation(toKey: (A) -> K, assemble: (K, A) -> B): ObservableMap<K, ObservableList<B>> {
    return AssociatedList(AggregatedList(this, toKey) { key, members -> Pair(key, members) }, { it.first }) { key, pair ->
        pair.second.map { assemble(key, it) }
    }
}

/**
 * val people: ObservableList<Person> = (..)
 * val heightToPeople: ObservableMap<Long, ObservableList<Person>> = people.associateByAggregation(Person::height)
 */
fun <K : Any, A : Any> ObservableList<out A>.associateByAggregation(toKey: (A) -> K): ObservableMap<K, ObservableList<A>> {
    return associateByAggregation(toKey) { key, value -> value }
}

/**
 * val nameToPerson: ObservableMap<String, Person> = (..)
 * val john: ObservableValue<Person?> = nameToPerson.getObservableValue("John")
 */
fun <K, V> ObservableMap<K, V>.getObservableValue(key: K): ObservableValue<V?> {
    val property = SimpleObjectProperty(get(key))
    addListener { change: MapChangeListener.Change<out K, out V> ->
        if (change.key == key) {
            // This is true both when a fresh element was inserted and when an existing was updated
            if (change.wasAdded()) {
                property.set(change.valueAdded)
            } else if (change.wasRemoved()) {
                property.set(null)
            }
        }
    }
    return property
}

/**
 * val nameToPerson: ObservableMap<String, Person> = (..)
 * val people: ObservableList<Person> = nameToPerson.getObservableValues()
 */
fun <K, V> ObservableMap<K, V>.getObservableValues(): ObservableList<V> {
    return MapValuesList.create(this) { it.value }
}

/**
 * val nameToPerson: ObservableMap<String, Person> = (..)
 * val people: ObservableList<Person> = nameToPerson.getObservableValues()
 */
fun <K, V> ObservableMap<K, V>.getObservableEntries(): ObservableList<Map.Entry<K, V>> {
    return MapValuesList.create(this) { it }
}

/**
 * val groups: ObservableList<ObservableList<Person>> = (..)
 * val allPeople: ObservableList<Person> = groups.concatenate()
 */
fun <A> ObservableList<ObservableList<A>>.concatenate(): ObservableList<A> {
    return ConcatenatedList(this)
}

/**
 * data class Person(val name: String, val managerName: String)
 * val people: ObservableList<Person> = (..)
 * val managerEmployeeMapping: ObservableList<Pair<Person, ObservableList<Person>>> =
 *   people.leftOuterJoin(people, Person::name, Person::managerName) { manager, employees -> Pair(manager, employees) }
 */
fun <A : Any, B : Any, C, K : Any> ObservableList<A>.leftOuterJoin(
        rightTable: ObservableList<B>,
        leftToJoinKey: (A) -> K,
        rightToJoinKey: (B) -> K,
        assemble: (A, ObservableList<B>) -> C
): ObservableList<C> {
    val joinedMap = leftOuterJoin(rightTable, leftToJoinKey, rightToJoinKey)
    return joinedMap.getObservableValues().map { pair ->
        pair.first.map { assemble(it, pair.second) }
    }.concatenate()
}

/**
 * data class Person(name: String, favouriteSpecies: Species)
 * data class Animal(name: String, species: Species)
 * val people: ObservableList<Person> = (..)
 * val animals: ObservableList<Animal> = (..)
 * val peopleToFavouriteAnimals: ObservableMap<Species, Pair<ObservableList<Person>, ObservableList<Animal>>> =
 *   people.leftOuterJoin(animals, Person::favouriteSpecies, Animal::species)
 *
 * This is the most general left join, given a joining key it returns for each key a pair of relevant elements from the
 * left and right tables. It is "left outer" in the sense that all members of the left table are guaranteed to be in
 * the result, but this may not be the case for the right table.
 */
fun <A : Any, B : Any, K : Any> ObservableList<A>.leftOuterJoin(
        rightTable: ObservableList<B>,
        leftToJoinKey: (A) -> K,
        rightToJoinKey: (B) -> K
): ObservableMap<K, Pair<ObservableList<A>, ObservableList<B>>> {
    val leftTableMap = associateByAggregation(leftToJoinKey)
    val rightTableMap = rightTable.associateByAggregation(rightToJoinKey)
    val joinedMap: ObservableMap<K, Pair<ObservableList<A>, ObservableList<B>>> =
            LeftOuterJoinedMap(leftTableMap, rightTableMap) { _key, left, rightValue ->
                Pair(left, ChosenList(rightValue.map { it ?: FXCollections.emptyObservableList() }))
            }
    return joinedMap
}

fun <A> ObservableList<A>.getValueAt(index: Int): ObservableValue<A?> {
    return Bindings.valueAt(this, index)
}
fun <A> ObservableList<A>.first(): ObservableValue<A?> {
    return getValueAt(0)
}
fun <A> ObservableList<A>.last(): ObservableValue<A?> {
    return Bindings.createObjectBinding({
        if (size > 0) {
            this[this.size - 1]
        } else {
            null
        }
    }, arrayOf(this))
}
