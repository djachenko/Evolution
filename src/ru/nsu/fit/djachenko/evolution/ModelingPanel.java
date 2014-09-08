package ru.nsu.fit.djachenko.evolution;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

//GUI panel representig modeling space
public class ModelingPanel extends JPanel
{
	private static final int CELL_SIZE = 10;

	private final Map<Integer, Color> colorCache = new HashMap<>();

	private JLabel[][] viewTable;

	ModelingPanel(int[][] table)
	{
		initUI(table.length, table[0].length);
	}

	private void initUI(int height, int width)
	{
		GridLayout layout = new GridLayout(height, width, 1, 1);

		setLayout(layout);

		viewTable = new JLabel[height][width];

		for (JLabel[] viewRow : viewTable)
		{
			for (int x = 0; x < viewRow.length; x++)
			{
				JLabel label = new JLabel();

				label.setPreferredSize(new Dimension(CELL_SIZE, CELL_SIZE));
				label.setBackground(Color.RED);
				label.setBorder(BorderFactory.createLineBorder(Color.BLACK));
				label.setOpaque(true);

				viewRow[x] = label;
				add(label);
			}
		}

		setPreferredSize(new Dimension(width * CELL_SIZE, height * CELL_SIZE));
	}

	void update(int[][] table)
	{
		for (int y = 0; y < viewTable.length; y++)
		{
			JLabel[] viewRow = viewTable[y];
			int[] row = table[y];

			for (int x = 0; x < viewRow.length; x++)
			{
				int cell = row[x];

				Color backgroundColor = colorCache.get(cell);

				//System.out.println("cell " + cell);

				if (backgroundColor == null)
				{
					backgroundColor = new Color(cell, cell, cell);

					colorCache.put(cell, backgroundColor);
				}

				viewRow[x].setBackground(backgroundColor);
			}
		}
	}
}
