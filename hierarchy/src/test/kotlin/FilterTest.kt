import kotlin.random.Random
import kotlin.test.*

class FilterTest {
  @Test
  fun testFilter() {
    val unfiltered: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
      intArrayOf(0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2))
    val filteredActual: Hierarchy = unfiltered.filter { nodeId -> nodeId % 3 != 0 }
    val filteredExpected: Hierarchy = ArrayBasedHierarchy(
      intArrayOf(1, 2, 5, 8, 10, 11),
      intArrayOf(0, 1, 1, 0, 1, 2))
    assertEquals(filteredExpected.formatString(), filteredActual.formatString())
  }

  @Test
  fun `empty hierarchy`() {
    val unfiltered = ArrayBasedHierarchy(intArrayOf(), intArrayOf())
    assertHierarchyEquals(unfiltered, unfiltered.filter { true })
  }

  @Test
  fun `all nodes pass`() {
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 1, 0))
    assertHierarchyEquals(unfiltered, unfiltered.filter { true })
  }

  @Test
  fun `no nodes pass`() {
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 1, 0))
    val expected = ArrayBasedHierarchy(intArrayOf(), intArrayOf())
    assertHierarchyEquals(expected, unfiltered.filter { false })
  }

  @Test
  fun `single node`() {
    val unfiltered = ArrayBasedHierarchy(intArrayOf(1), intArrayOf(0))
    val empty = ArrayBasedHierarchy(intArrayOf(), intArrayOf())
    assertHierarchyEquals(unfiltered, unfiltered.filter { true })
    assertHierarchyEquals(empty, unfiltered.filter { false })
  }

  @Test
  fun `failing root removes its whole tree but not the next tree`() {
    // 1        <- fails
    // - 2
    // - - 3
    // 4
    // - 5
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 0, 1))
    val expected = ArrayBasedHierarchy(
      intArrayOf(4, 5),
      intArrayOf(0, 1))
    assertHierarchyEquals(expected, unfiltered.filter { it != 1 })
  }

  @Test
  fun `failing node removes descendants even if they pass`() {
    // 1
    // - 2      <- fails
    // - - 3    <- passes, but removed with 2
    // - - - 4  <- passes, but removed with 2
    // - 5      <- sibling of 2, kept
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 3, 1))
    val expected = ArrayBasedHierarchy(
      intArrayOf(1, 5),
      intArrayOf(0, 1))
    assertHierarchyEquals(expected, unfiltered.filter { it != 2 })
  }

  @Test
  fun `failing leaf removes only itself`() {
    // 1
    // - 2      <- fails
    // - 3
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3),
      intArrayOf(0, 1, 1))
    val expected = ArrayBasedHierarchy(
      intArrayOf(1, 3),
      intArrayOf(0, 1))
    assertHierarchyEquals(expected, unfiltered.filter { it != 2 })
  }

  @Test
  fun `shallower node after removed subtree is kept`() {
    // 1
    // - 2
    // - - 3    <- fails
    // - - - 4
    // - 5      <- depth drops from 3 to 1: outside 3's subtree
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5),
      intArrayOf(0, 1, 2, 3, 1))
    val expected = ArrayBasedHierarchy(
      intArrayOf(1, 2, 5),
      intArrayOf(0, 1, 1))
    assertHierarchyEquals(expected, unfiltered.filter { it != 3 })
  }

  @Test
  fun `failure inside removed subtree does not end the removal`() {
    // 1
    // - 2      <- fails
    // - - 3
    // - - - 4  <- also fails, but is already removed
    // - - 5    <- still inside 2's subtree: must stay removed
    // - 6
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6),
      intArrayOf(0, 1, 2, 3, 2, 1))
    val expected = ArrayBasedHierarchy(
      intArrayOf(1, 6),
      intArrayOf(0, 1))
    assertHierarchyEquals(expected, unfiltered.filter { it != 2 && it != 4 })
  }

  @Test
  fun `node that ends removed subtree is still tested`() {
    // 1
    // - 2      <- fails
    // - - 3
    // - 4      <- ends 2's subtree, and fails itself
    // - - 5
    // - 6
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6),
      intArrayOf(0, 1, 2, 1, 2, 1))
    val expected = ArrayBasedHierarchy(
      intArrayOf(1, 6),
      intArrayOf(0, 1))
    assertHierarchyEquals(expected, unfiltered.filter { it != 2 && it != 4 })
  }

  @Test
  fun `roots only`() {
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4),
      intArrayOf(0, 0, 0, 0))
    val expected = ArrayBasedHierarchy(
      intArrayOf(1, 3),
      intArrayOf(0, 0))
    assertHierarchyEquals(expected, unfiltered.filter { it % 2 != 0 })
  }

  @Test
  fun `predicate is not called for descendants of failing node`() {
    // 1
    // - 2      <- fails
    // - - 3    <- predicate should not be called
    // - - - 4  <- predicate should not be called
    // - 5
    // 6
    val unfiltered = ArrayBasedHierarchy(
      intArrayOf(1, 2, 3, 4, 5, 6),
      intArrayOf(0, 1, 2, 3, 1, 0))
    val calledFor = mutableListOf<Int>()
    unfiltered.filter { calledFor += it; it != 2 }
    assertEquals(listOf(1, 2, 5, 6), calledFor)
  }

  @Test
  fun `deep chain does not overflow the stack`() {
    // 0, - 1, - - 2, ... one node per level, node ID == depth
    val n = 100_000
    val unfiltered = ArrayBasedHierarchy(IntArray(n) { it }, IntArray(n) { it })
    val expected = ArrayBasedHierarchy(IntArray(n / 2) { it }, IntArray(n / 2) { it })
    assertHierarchyEquals(expected, unfiltered.filter { it != n / 2 })
  }

  private fun assertHierarchyEquals(expected: Hierarchy, actual: Hierarchy) =
    assertEquals(expected.formatString(), actual.formatString())
}
