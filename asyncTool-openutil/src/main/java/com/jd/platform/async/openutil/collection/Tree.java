package com.jd.platform.async.openutil.collection;

import java.util.Collection;

/**
 * @author create by TcSnZh on 2021/5/15-下午7:58
 */
public interface Tree {
    interface Node {
        default Node getParent(){
            throw new UnsupportedOperationException("Get parent node is not supported.");
        }

        Collection<? extends Node> getChildren();
    }
}
