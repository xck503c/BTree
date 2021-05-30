# B Tree

## version change description
###version 1.0.0

date: 2021-05-23

refer to: [https://blog.csdn.net/jimo_lonely/article/details/82716142](https://blog.csdn.net/jimo_lonely/article/details/82716142)

the program will generate a file for each node.

no cache page:
1. insert 1000 use 3555ms
2. search 1000 use 712ms

###version 1.1.0

date: 2021-05-29

the program will generate tow files, which have the following functions:
1. index file: key in k-v pair and pointer to the value in data file.
2. data file: value in k-v pair.

no cache page, branchingFactor=8, test once: 
1. insert 1000 use 10638ms
2. search 1000 use 437ms

###version 1.1.1

date: 2021-05-30

fix:
1. the problem of exceptional index data block.

perf：
1. add tail of data segment offset information to the data page header.

no cache page, branchingFractor=8, test once:
1. insert 1000 use 1367ms
2. search 1000 use 382ms


# B plus Tree

refer to: [https://github.com/youkaichao/minidb](https://github.com/youkaichao/minidb)


The project hasn't started yet.