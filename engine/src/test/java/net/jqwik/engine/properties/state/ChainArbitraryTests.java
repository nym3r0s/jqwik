package net.jqwik.engine.properties.state;

import java.util.*;
import java.util.function.*;

import net.jqwik.api.*;
import net.jqwik.api.state.*;
import net.jqwik.testing.*;

import static org.assertj.core.api.Assertions.*;

class ChainArbitraryTests {

	@Example
	void chainWithSingleGenerator(@ForAll Random random) {
		Function<Supplier<Integer>, Arbitrary<Chains.Mutator<Integer>>> growBelow100OtherwiseShrink = intSupplier -> {
			int last = intSupplier.get();
			if (last < 100) {
				return Arbitraries.integers().between(0, 10).map(i -> t -> t + i);
			} else {
				return Arbitraries.integers().between(1, 10).map(i -> t -> t - i);
			}
		};
		Arbitrary<Chain<Integer>> chains = Chains.chains(
			() -> 1,
			growBelow100OtherwiseShrink
		).ofMaxSize(50);

		Shrinkable<Chain<Integer>> chainShrinkable = chains.generator(100).next(random);

		Chain<Integer> chain = chainShrinkable.value();

		assertThat(chain.maxSize()).isEqualTo(50);
		assertThat(chain.countIterations()).isEqualTo(0);

		Iterator<Integer> iterator = chain.start();
		int last = 1;
		while (iterator.hasNext()) {
			int next = iterator.next();
			if (last < 100) {
				assertThat(next).isGreaterThanOrEqualTo(last);
			} else {
				assertThat(next).isLessThan(last);
			}
			last = next;
		}

		assertThat(chain.countIterations()).isEqualTo(50);
	}

	@Example
	void chainWithSeveralGenerators(@ForAll Random random) {
		Function<Supplier<Integer>, Arbitrary<Chains.Mutator<Integer>>> growBelow100otherwiseShrink = intSupplier -> {
			int last = intSupplier.get();
			if (last < 100) {
				return Arbitraries.integers().between(0, 10).map(i -> t -> t + i);
			} else {
				return Arbitraries.integers().between(1, 10).map(i -> t -> t - i);
			}
		};

		Function<Supplier<Integer>, Arbitrary<Chains.Mutator<Integer>>> resetToValueBetween0andLastAbsolute = supplier -> {
			int last = supplier.get();
			return Arbitraries.integers().between(0, Math.abs(last)).map(value -> (ignore -> value));
		};

		Arbitrary<Chain<Integer>> chains = Chains.chains(
			() -> 1,
			ignore -> Arbitraries.just(i -> i - 1),
			growBelow100otherwiseShrink,
			resetToValueBetween0andLastAbsolute
		).ofMaxSize(50);

		Shrinkable<Chain<Integer>> chainShrinkable = chains.generator(100).next(random);

		Chain<Integer> chain = chainShrinkable.value();

		assertThat(chain.maxSize()).isEqualTo(50);
		assertThat(chain.countIterations()).isEqualTo(0);

		Iterator<Integer> iterator = chain.start();
		iterator.forEachRemaining(ignore -> {});

		assertThat(chain.countIterations()).isEqualTo(50);
	}

	@Example
	void chainCanBeRerunWithSameValues(@ForAll Random random) {
		Arbitrary<Chain<Integer>> chains = Chains.chains(
			() -> 1,
			ignore -> Arbitraries.integers().between(0, 10).map(i -> t -> t + i)
		);

		Shrinkable<Chain<Integer>> chainShrinkable = chains.generator(100).next(random);

		Chain<Integer> chain = chainShrinkable.value();

		List<Integer> values1 = collectAllValues(chain);
		List<Integer> values2 = collectAllValues(chain);
		assertThat(values2).isEqualTo(values1);

	}

	private <T> List<T> collectAllValues(Chain<T> chain) {
		List<T> values = new ArrayList<>();
		for (T i : chain) {
			values.add(i);
		}
		return values;
	}

	@Example
	void shrinkChainWithoutStateAccessToEnd(@ForAll Random random) {
		Arbitrary<Chain<Integer>> chains = Chains.chains(
			() -> 1,
			ignore -> Arbitraries.integers().between(0, 10).map(i -> t -> t + i)
		).ofMaxSize(5);

		TestingFalsifier<Chain<Integer>> falsifier = chain -> {
			for (Integer integer : chain) {
				// consume iterator
			}
			return false;
		};
		Chain<Integer> chain = ShrinkingSupport.falsifyThenShrink(chains, random, falsifier);

		assertThat(chain.countIterations()).isEqualTo(5);
		assertThat(collectAllValues(chain)).contains(1, 1, 1, 1, 1);
	}

	@Example
	void shrinkChainWithStateAccessToEnd(@ForAll Random random) {
		Arbitrary<Chain<Integer>> chains = Chains.chains(
			() -> 1,
			supplier -> {
				supplier.get(); // To make shrinkable think the last value is being used
				return Arbitraries.integers().between(0, 10).map(i -> t -> t + i);
			}
		).ofMaxSize(5);

		TestingFalsifier<Chain<Integer>> falsifier = chain -> {
			for (Integer integer : chain) {
				// consume iterator
			}
			return false;
		};
		Chain<Integer> chain = ShrinkingSupport.falsifyThenShrink(chains, random, falsifier);

		assertThat(chain.countIterations()).isEqualTo(5);
		assertThat(collectAllValues(chain)).contains(1, 1, 1, 1, 1);
	}
}
