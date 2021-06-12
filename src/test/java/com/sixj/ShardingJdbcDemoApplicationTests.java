package com.sixj;

import com.sixj.entry.COrder;
import com.sixj.repository.COrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Repeat;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.List;
import java.util.Random;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShardingJdbcDemoApplication.class)
class ShardingJdbcDemoApplicationTests {

    @Autowired
    private COrderRepository cOrderRepository;


    @Test
    public void testAdd() {
        for (int i = 100; i <120; i++) {
            COrder cOrder = new COrder();
            cOrder.setDel(false);
            cOrder.setUserId(i);
            cOrder.setCompanyId(new Random().nextInt(10));
            cOrder.setPublishUserId(new Random().nextInt(10));
            cOrder.setPositionId(new Random().nextInt(10));
            cOrder.setResumeType(new Random().nextInt(1));
            cOrder.setStatus("ARRANGE_INTERVIEW");
            cOrder.setCreateTime(new Date());
            cOrder.setUpdateTime(new Date());
            cOrderRepository.saveAndFlush(cOrder);
        }
    }

    @Test
    public void testFind() {
        List<COrder> cOrderList = cOrderRepository.findAll();
        cOrderList.forEach(cOrder -> {
            System.out.println(cOrder.toString());
        });
    }

}
