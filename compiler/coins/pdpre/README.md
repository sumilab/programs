This program is an implementation of PDPRE in [COINS](https://sourceforge.net/projects/coins-project/) compiler.

Example of compilation
`java -classpath ./classes coins.driver.Driver -coins:target=x86_64,ssa-opt=divex/prun/pdpre/srd3`

* `divex`: making each statement three-address code
* `prun`: converting program from the normal form to the SSA form
* `pdpre`: applying PDPRE
* `srd3`: converting program from the SSA form to the normal form

# Publication
1. Takuna Uemura and Yasunobu Sumikawa, [Demand-driven PRE using Profile Information](https://www.jstage.jst.go.jp/article/ipsjjip/33/0/33_198/_article/-char/ja), Journal of Information Processing, Vol. 66, No. 3, pp. 198-209, 2025.
2. [Programs and Documents](https://zenodo.org/records/13363792)
