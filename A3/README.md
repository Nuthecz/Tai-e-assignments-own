# A3

## 理解

- 这次实验是在参考别人的残缺代码后，跟着样例一步一步实现的
- 这里通过 visited 来对 cfg 中可以到达的 stmt 进行添加，注意 HashSet 无序不重复
- 这里需要注意对于 IF 语句的跳转，即遍历 cfg 的过程中，遇到正确的 if 语句进行跳转，这样就可以剔除错误的分支，switch 也是一样的道理，而对于无用赋值，只需要考虑它的条件进行删除即可
- 注意 Entry 和 Exit 需要从 deadCode 中删除，后者不会被 visited 访问到，所以需要手动从 deadCode 中删除 

> [参考代码](https://github.com/pascal-lab/Tai-e-assignments/commit/90a0b5d4e46b6ecc43ead61d7efd3e408ec79aaf)
>
> PS: 过了本地样例就提交了，然后居然过了。本来以为fall-through不对的，后来看自己写的样例的三地址码，发现如果没有break的话，我的代码会按顺序执行，也就是说向下继续执行，这样就解决了fall-through的问题