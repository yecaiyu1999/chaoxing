package cn.yecaiyu.comparator;

import cn.yecaiyu.entity.ChxSection;

import java.util.Comparator;

public class ChxSectionComparator implements Comparator<ChxSection> {

    @Override
    public int compare(ChxSection o1, ChxSection o2) {
        String[] o1Split = o1.getLabel().split("\\.");
        String[] o2Split = o2.getLabel().split("\\.");
        // 获取较长的哪一个
        int minLength = Math.min(o1Split.length, o2Split.length);
        for (int i = 0; i < minLength; i++) {
            int o1Num = Integer.parseInt(o1Split[i]);
            int o2Num = Integer.parseInt(o2Split[i]);
            if (o1Num > o2Num) {
                return 1;
            } else if (o1Num < o2Num) {
                return -1;
            }
        }
        return 0;
    }
}
