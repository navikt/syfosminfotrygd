package no.nav.syfo.rules.dsl

sealed class TreeNode<T, R>

class ResultNode<T, R>(val result: R) : TreeNode<T, R>()

class RuleNode<T, R> internal constructor(val rule: T) : TreeNode<T, R>() {
    lateinit var yes: TreeNode<T, R>
    lateinit var no: TreeNode<T, R>

    internal fun yes(rule: T, init: RuleNode<T, R>.() -> Unit) {
        yes = RuleNode<T, R>(rule).apply(init)
    }

    internal fun no(rule: T, init: RuleNode<T, R>.() -> Unit) {
        no = RuleNode<T, R>(rule).apply(init)
    }

    internal fun yes(result: R) {
        yes = ResultNode(result)
    }

    internal fun no(result: R) {
        no = ResultNode(result)
    }
}

fun <T, R> tree(rule: T, init: RuleNode<T, R>.() -> Unit): RuleNode<T, R> =
    RuleNode<T, R>(rule).apply(init)

fun <T, R> rule(rule: T, init: RuleNode<T, R>.() -> Unit): RuleNode<T, R> =
    RuleNode<T, R>(rule).apply(init)
