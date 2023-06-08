This program is an implementation of GLIA for nomal form program in [COINS](https://sourceforge.net/projects/coins-project/) compiler. This implementation requires DivideExpression3 before.

Example of compilation
`java -classpath ./classes coins.driver.Driver -coins:target=x86_64,ssa-opt=esplt/divex3/glia/prun/dce/srd3`

* `esplt`: critical edge elimination
* `divex3`: applying DivideExpression3 that extracts array references
* `glia`: applying GLIA
* `prun`: converting program from the normal form to the SSA form
* `dce`: applying dead code elimination
* `srd3`: converting program from the SSA form to the normal form

`MDGLIA.java` is an extended GLIA to aggregate array references accessing to the same arrays.

# Publication
1. Yasunobu Sumikawa and Munehiro Takimoto, [Global Load Instruction Aggregation Based on Dimensions of Arrays](https://www.sciencedirect.com/science/article/abs/pii/S0045790615003067), Computers and Electrical Engineering, Elsevier, Vol. 50, pp. 180 -- 199, 2016.
2. Yasunobu Sumikawa and Munehiro Takimoto, [Global Load Instruction Aggregation Based on Array Dimensions](https://ieeexplore.ieee.org/document/6916449), In Proceedings of the 6th International Symposium on Parallel Architectures, Algorithms and Programming, PAAP'14, IEEE Computer Society, pp. 123 -- 129, 2014. 
3. Yasunobu Sumikawa and Munehiro Takimoto, [Global Load Instruction Aggregation Based on Code Motion](https://ieeexplore.ieee.org/document/6424750), In Proceedings of the 5th International Symposium on Parallel Architectures, Algorithms and Programming, PAAP'12, IEEE Computer Society, pp. 149 -- 156, 2012. 
