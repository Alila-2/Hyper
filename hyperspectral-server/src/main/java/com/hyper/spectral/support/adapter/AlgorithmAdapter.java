package com.hyper.spectral.support.adapter;

import java.util.List;

/**
 * 算法适配层扩展点，当前仅提供可用算法目录。
 */
public interface AlgorithmAdapter {

    List<String> listAvailableAlgorithms();
}
