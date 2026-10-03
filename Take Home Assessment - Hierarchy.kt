import kotlin.test.*

// The task:
// 1. Read and understand the Hierarchy data structure described in this file.
// 2. Implement filter() function.
// 3. Implement more test cases.
//
// The task should take 30-90 minutes.
//
// When assessing the submission, we will pay attention to:
// - correctness, efficiency, and clarity of the code;
// - the test cases.

/**
 * A `Hierarchy` stores an arbitrary _forest_ (an ordered collection of ordered trees)
 * as an array of node IDs in the order of DFS traversal, combined with a parallel array of node depths.
 *
 * Parent-child relationships are identified by the position in the array and the associated depth.
 * Each tree root has depth 0, its children have depth 1 and follow it in the array, their children have depth 2 and follow them, etc.
 *
 * Example:
 * ```
 * nodeIds: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
 * depths:  0, 1, 2, 3, 1, 0, 1, 0, 1, 1, 2
 * ```
 *
 * the forest can be visualized as follows:
 * ```
 * 1
 * - 2
 * - - 3
 * - - - 4
 * - 5
 * 6
 * - 7
 * 8
 * - 9
 * - 10
 * - - 11
 *```
 * 1 is a parent of 2 and 5, 2 is a parent of 3, etc. Note that depth is equal to the number of hyphens for each node.
 *
 * Invariants on the depths array:
 *  * Depth of the first element is 0.
 *  * If the depth of a node is `D`, the depth of the next node in the array can be:
 *      * `D + 1` if the next node is a child of this node;
 *      * `D` if the next node is a sibling of this node;
 *      * `d < D` - in this case the next node is not related to this node.
 */
interface Hierarchy {
  /** The number of nodes in the hierarchy. */
  val size: Int

  /**
   * Returns the unique ID of the node identified by the hierarchy index. The depth for this node will be `depth(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun nodeId(index: Int): Int

  /**
   * Returns the depth of the node identified by the hierarchy index. The unique ID for this node will be `nodeId(index)`.
   * @param index must be non-negative and less than [size]
   * */
  fun depth(index: Int): Int

  fun formatString(): String {
    return (0 until size).joinToString(
      separator = ", ",
      prefix = "[",
      postfix = "]"
    ) { i -> "${nodeId(i)}:${depth(i)}" }
  }
}

/**
 * A node is present in the filtered hierarchy iff its node ID passes the predicate and all of its ancestors pass it as well.
 */
fun Hierarchy.filter(nodeIdPredicate: (Int) -> Boolean): Hierarchy {
  val filteredNodeIds: MutableList<Int> = mutableListOf()
  val filteredDepths: MutableList<Int> = mutableListOf()

  var depthToFilter: Int? = null
  for (i in 0..<this.size) {
    val nodeId = this.nodeId(i)
    val depth = this.depth(i)

    // if depthToFilter is set, skip this node
    if (depthToFilter != null && depthToFilter < depth) {
      continue
    }

    // skip if predicate is false and save current depth
    if (!nodeIdPredicate(nodeId)) {
      depthToFilter = depth
      continue
    }

    // reaching this means we are either in a lower or same depth as we were filtering, so reset depthToFilter
    // and save this node and its depth
    depthToFilter = null
    filteredNodeIds.add(nodeId)
    filteredDepths.add(depth)
  }

  return ArrayBasedHierarchy(filteredNodeIds.toIntArray(), filteredDepths.toIntArray())
}

class ArrayBasedHierarchy(
  private val myNodeIds: IntArray,
  private val myDepths: IntArray,
) : Hierarchy {
  override val size: Int = myDepths.size

  override fun nodeId(index: Int): Int = myNodeIds[index]

  override fun depth(index: Int): Int = myDepths[index]
}

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
