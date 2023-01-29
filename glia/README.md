This program is an implementation of GLIA for nomal form program in COINS compiler. This implementation requires DivideExpression3 before.

Example of compilation
`java -classpath ./classes coins.driver.Driver -coins:target=x86_64,ssa-opt=esplt/divex3/glia/prun/dce/srd3`

* `esplt`: critical edge elimination
* `divex3`: applying DivideExpression3 that extracts array references
* `prun`: converting program from normal form to SSA form
* `dce`: dead code elimination
* `srd3`: converting program from SSA form to normal form

# Publication
1. Yasunobu Sumikawa and Munehiro Takimoto, [Global Load Instruction Aggregation Based on Dimensions of Arrays](https://www.sciencedirect.com/science/article/abs/pii/S0045790615003067), Computers and Electrical Engineering, Elsevier, Vol. 50, pp. 180 -- 199, 2016.
2. Yasunobu Sumikawa and Munehiro Takimoto, [Global Load Instruction Aggregation Based on Array Dimensions](https://ieeexplore.ieee.org/document/6916449), In Proceedings of the 6th International Symposium on Parallel Architectures, Algorithms and Programming, PAAP'14, IEEE Computer Society, pp. 123 -- 129, 2014. 
3. Yasunobu Sumikawa and Munehiro Takimoto, [Global Load Instruction Aggregation Based on Code Motion](https://ieeexplore.ieee.org/document/6424750), In Proceedings of the 5th International Symposium on Parallel Architectures, Algorithms and Programming, PAAP'12, IEEE Computer Society, pp. 149 -- 156, 2012. 