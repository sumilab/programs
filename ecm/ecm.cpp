#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <tuple>
#include <algorithm>

using std::string;
using std::vector;
template<class T>
using matrix = std::vector<std::vector<T>>;
using point = std::pair<int, int>;
using edge_set = std::vector<std::pair<int, int>>;


// split a string at delimiter
// default delimiter is ','
vector<double> split (const string &input, char delimiter);

// solve ordered graph bipartite maximum matching
// input    : edge weight matrix for complete bipartite graph (0 based index)
// output   : set of edge (1 based index)
edge_set ordered_bipartite_matching (const matrix<double> &data_table);

int main() {
    // file name (change as apporopriate)
    const string input_file_name = "test.csv";
    const string output_file_name = "answer.tsv";

    // input
    std::ifstream input_file(input_file_name, std::ios::in);

    string buffer;
    matrix<double> data_table;
    while (not input_file.eof()) {
        std::getline(input_file, buffer);
        data_table.push_back(split(buffer, ','));
    }

    // solve
    const edge_set answer = ordered_bipartite_matching(data_table);

    // output
    //std::ofstream output_file(output_file_name, std::ios::out);

    for (std::size_t i = 0; i < answer.size(); i++) {
      //output_file << answer[i].first << "\t" << answer[i].second;
      //if (i + 1 < answer.size()) {
      //    output_file << "\t\t";
      //}
        std::cout << answer[i].first << "\t" << answer[i].second;
	if (i + 1 < answer.size()) std::cout << "\t\t";
	//std::cout << "hello";
    }

}


vector<double> split (const string &input, char delimiter = ',') {
    vector<double> result;
    string buffer;
    for (std::size_t i = 0; i < input.length(); i++) {
        if (input[i] == delimiter or i + 1 == input.length()) {
            result.push_back(std::stod(buffer));
            buffer.clear();
        } else {
            buffer += input[i];
        }
    }

    return result;
}


edge_set ordered_bipartite_matching (const matrix<double> &data_table) {
    const std::size_t h = data_table.size(), w = data_table[0].size();
    matrix<double> dp(h + 1, vector<double>(w + 1, 0));
    matrix<std::pair<double, point>> range_max(h + 1, vector<std::pair<double, point>>(w + 1));
    
    // initialization
    for (std::size_t i = 0; i < h + 1; i++) {
        dp[i][0] = 0.0;
        range_max[i][0] = std::make_pair(0.0, point(-1, -1));
    }
    for (std::size_t j = 0; j < w + 1; j++) {
        dp[0][j] = 0.0;
        range_max[0][j] = std::make_pair(0.0, point(-1, -1));
    }

    // find the maximum weight
    for (std::size_t i = 0; i < h; i++) for (std::size_t j = 0; j < w; j++) {
        // update dp
        dp[i + 1][j + 1] = range_max[i][j].first + data_table[i][j];
        // update renge_max
        range_max[i + 1][j + 1] = std::max(range_max[i + 1][j], range_max[i][j + 1]);
        if (range_max[i + 1][j + 1].first < dp[i + 1][j + 1]) {
            range_max[i + 1][j + 1] = std::make_pair(dp[i + 1][j + 1], point(i, j));
        }
    }

    // find the set of edge
    edge_set answer;
    int r = -1, c = -1;
    double maximum_weight = 0;
    for (int i = 1; i < h + 1; i++) {
        if (maximum_weight < dp[i][w]) {
            maximum_weight = dp[i][w];
            r = i - 1;
            c = w - 1;
        }
    }
    for (int j = 1; j < w + 1; j++) {
        if (maximum_weight < dp[h][j]) {
            maximum_weight = dp[h][j];
            r = h - 1;
            c = j - 1;
        }
    }

    while (r != -1 and c != -1) {
        answer.emplace_back(r + 1, c + 1);
        std::tie(r, c) = range_max[r][c].second;
    }

    std::reverse(answer.begin(), answer.end());
    return answer;

}
