/*
 * Copyright (c) 2026 Nicholas DellaGrotte
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.ndellagrotte.ordinaryai.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class MobListTest {

    private static final Map<String, Class<?>> IDS = Map.of("animal", Animal.class, "dog", Dog.class, "puppy",
        Puppy.class, "cat", Cat.class
    );

    private final List<String> warnings = new ArrayList<>();

    private MobList parse(String... lines) {
        return MobList.parse(lines, IDS::get, warnings::add);
    }

    @Test
    void matchesSubclassesUnlessMarkedExact() {
        MobList list = parse("dog 0.5");
        assertThat(list.contains(Dog.class)).isTrue();
        assertThat(list.contains(Puppy.class)).isTrue();
        assertThat(list.contains(Animal.class)).isFalse();
        assertThat(list.contains(Cat.class)).isFalse();

        MobList exact = parse("~dog 0.5");
        assertThat(exact.contains(Dog.class)).isTrue();
        assertThat(exact.contains(Puppy.class)).isFalse();
    }

    @Test
    void mostSpecificEntryWinsRegardlessOfOrder() {
        assertThat(parse("animal 0.1", "dog 0.2", "puppy 0.3").chancesFor(Puppy.class)).containsExactly(0.3F);
        assertThat(parse("puppy 0.3", "dog 0.2", "animal 0.1").chancesFor(Puppy.class)).containsExactly(0.3F);
        assertThat(parse("animal 0.1", "dog 0.2").chancesFor(Puppy.class)).containsExactly(0.2F);
        assertThat(parse("animal 0.1", "dog 0.2").chancesFor(Cat.class)).containsExactly(0.1F);
    }

    @Test
    void exactEntryBeatsInheritingEntryForTheSameClass() {
        assertThat(parse("dog 0.2", "~dog 0.9").chancesFor(Dog.class)).containsExactly(0.9F);
        assertThat(parse("~dog 0.9", "dog 0.2").chancesFor(Dog.class)).containsExactly(0.9F);
        assertThat(parse("~dog 0.9", "dog 0.2").chancesFor(Puppy.class)).containsExactly(0.2F);
    }

    @Test
    void readsSeveralChancesPerEntry() {
        assertThat(parse("  dog   0.1  0.05 0.02 ").chancesFor(Dog.class)).containsExactly(0.1F, 0.05F, 0.02F);
        assertThat(warnings).isEmpty();
    }

    @Test
    void entryWithoutChancesIsAMatchWithNoValues() {
        MobList list = parse("dog");
        assertThat(list.chancesFor(Dog.class)).isEmpty();
        assertThat(list.chancesFor(Cat.class)).isNull();
    }

    @Test
    void entryWithoutChancesAlwaysPassesWithoutUsingRandomness() {
        Random random = new Random(1L);
        long expectedNext = new Random(1L).nextLong();
        assertThat(parse("dog").roll(Dog.class, random)).isTrue();
        assertThat(random.nextLong()).isEqualTo(expectedNext);
    }

    @Test
    void rollUsesTheFirstChance() {
        Random random = new Random(7L);
        assertThat(parse("dog 1.0 0.0").roll(Dog.class, random)).isTrue();
        assertThat(parse("dog 0.0 1.0").roll(Dog.class, random)).isFalse();
        assertThat(parse("dog 1.0").roll(Cat.class, random)).isFalse();
    }

    @Test
    void unknownIdsAreReportedAndSkipped() {
        MobList list = parse("unicorn 1.0", "dog 1.0", "");
        assertThat(list.contains(Dog.class)).isTrue();
        assertThat(warnings).singleElement().asString().contains("unicorn");
    }

    @Test
    void invalidChancesBecomeZeroWithAWarning() {
        MobList list = parse("dog 1.5 abc -0.1 0.25");
        assertThat(list.chancesFor(Dog.class)).containsExactly(0.0F, 0.0F, 0.0F, 0.25F);
        assertThat(warnings).hasSize(3);
    }

    @Test
    void emptyListMatchesNothing() {
        MobList list = parse();
        assertThat(list.isEmpty()).isTrue();
        assertThat(list.contains(Dog.class)).isFalse();
    }

    private static class Animal { }

    private static class Dog extends Animal { }

    private static final class Puppy extends Dog { }

    private static final class Cat extends Animal { }

}
