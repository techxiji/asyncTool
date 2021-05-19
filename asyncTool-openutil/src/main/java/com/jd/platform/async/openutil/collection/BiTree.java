package com.jd.platform.async.openutil.collection;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

/**
 * 二叉树
 *
 * @author create by TcSnZh on 2021/5/15-下午8:00
 */
public interface BiTree extends Tree {
    // todo

    /**
     * 二叉树节点
     */
    interface BiNode extends Node {
        @Override
        default BiNode getParent() {
            throw new UnsupportedOperationException("Get parent node is not supported.");
        }

        BiNode getLeft();

        BiNode getRight();

        @Override
        default Collection<? extends BiNode> getChildren() {
            return Arrays.asList(getLeft(), getRight());
        }
    }

    /**
     * 返回一个通俗易懂的字符画。
     * <p/>
     * 从leetcode上抄的。若有侵权请联系 {@code zh0u.he@qq.com}，将会删除。
     *
     * @param node        根节点
     * @param provideName 节点显示在图中的名字。
     * @param <N>         根节点泛型
     * @return 返回一个字符画
     */
    static <N extends BiNode> String toPrettyString(N node, Function<N, String> provideName) {
        StringBuilder sb = new StringBuilder();
        //noinspection unchecked
        _toPrettyString(node, "", true, sb, (Function) provideName);
        return sb.toString();
    }

    /**
     * jdk8没有private static，只能加条下划线意思意思了。
     */
    static <N extends BiNode> void _toPrettyString(N node,
                                                   String prefix,
                                                   boolean isLeft,
                                                   StringBuilder sb,
                                                   Function<BiNode, String> provideName) {
        if (node == null) {
            sb.append("(Empty tree)");
            return;
        }

        BiNode right = node.getRight();
        if (right != null) {
            _toPrettyString(right, prefix + (isLeft ? "│   " : "    "), false, sb, provideName);
        }

        sb.append(prefix).append(isLeft ? "└── " : "┌── ").append(provideName.apply(node)).append('\n');

        BiNode left = node.getLeft();
        if (left != null) {
            _toPrettyString(left, prefix + (isLeft ? "    " : "│   "), true, sb, provideName);
        }
    }
}
